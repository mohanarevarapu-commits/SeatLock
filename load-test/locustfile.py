"""Locust load test for SeatLock: concert fans browsing, holding seats and checking out.

Each virtual user is one fan. It loads the seat map, picks seats it saw as available
(usually from the front sections, where everyone competes), holds them, and then checks out,
or sometimes abandons and releases the hold. A smaller share uses the queued checkout and polls
for the outcome. At the end the script checks that no seat was sold twice.

Start the app with the per-IP rate limit off (all virtual users share one IP):

    ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo \\
        -Dspring-boot.run.arguments=--seatlock.rate-limit.enabled=false

Then, from the repository root:

    pip install -r load-test/requirements.txt
    locust --config load-test/locust.conf          # 500 users, headless, HTML + CSV report

Or run `locust -f load-test/locustfile.py --host http://localhost:8080` and use the web UI at
http://localhost:8089. Environment variables: SEATLOCK_EVENT_ID (default 1).
"""

import os
import random
import threading
import time
from collections import Counter

from locust import HttpUser, between, events, task

EVENT_ID = int(os.environ.get("SEATLOCK_EVENT_ID", "1"))
SEATS_PER_ORDER = (1, 4)
HOT_SHARE = 0.7          # share of orders that go for the front sections everyone wants
HOT_SEATS = 1500         # the first N seats of the map count as "front sections"
ABANDON_SHARE = 0.15     # share of holds released instead of checked out
QUEUE_POLLS = 20

# Shared across all users in this process, for the end-of-run report and double-sale check.
_lock = threading.Lock()
_outcomes = Counter()
_sold_seats = []


def _record(outcome, seats=()):
    with _lock:
        _outcomes[outcome] += 1
        _sold_seats.extend(seats)


class ConcertFan(HttpUser):
    wait_time = between(0.5, 2)

    def on_start(self):
        self.ref = f"fan-{random.getrandbits(48):x}"
        self.available = []
        self.browse_seat_map()

    @task(5)
    def browse_seat_map(self):
        with self.client.get(f"/api/events/{EVENT_ID}/seat-map", name="GET seat-map",
                             catch_response=True) as resp:
            if resp.status_code != 200:
                resp.failure(f"HTTP {resp.status_code}")
                return
            seats = [s["eventSeatId"] for section in resp.json()["sections"] for s in section["seats"]
                     if s["status"] == "AVAILABLE"]
            self.available = seats

    @task(4)
    def hold_and_checkout(self):
        seats = self._pick_seats()
        if not seats:
            return
        with self.client.post(f"/api/events/{EVENT_ID}/holds", name="POST hold",
                              json={"customerRef": self.ref, "eventSeatIds": seats},
                              catch_response=True) as resp:
            if resp.status_code == 409:
                # Someone else got there first: the system working as designed, not an error.
                resp.success()
                _record("hold lost (seat taken)")
                self._forget(seats)
                return
            if resp.status_code != 201:
                resp.failure(f"HTTP {resp.status_code}")
                return
            hold_id = resp.json()["id"]
        _record("hold won")
        self._forget(seats)

        time.sleep(random.uniform(0.5, 2))  # time spent on the payment page
        if random.random() < ABANDON_SHARE:
            self.client.delete(f"/api/holds/{hold_id}", name="DELETE hold (abandon)")
            _record("hold abandoned")
            return
        self._checkout(hold_id)

    @task(1)
    def queued_checkout(self):
        seats = self._pick_seats()
        if not seats:
            return
        with self.client.post(f"/api/events/{EVENT_ID}/purchase-requests", name="POST purchase-request",
                              json={"customerRef": self.ref, "eventSeatIds": seats},
                              catch_response=True) as resp:
            if resp.status_code != 202:
                resp.failure(f"HTTP {resp.status_code}")
                return
            request_id = resp.json()["requestId"]
        self._forget(seats)

        for _ in range(QUEUE_POLLS):
            time.sleep(0.25)
            status = self.client.get(f"/api/purchase-requests/{request_id}",
                                     name="GET purchase-request").json()["status"]
            if status != "QUEUED":
                break
        _record(f"queued request {status}")
        if status == "HELD":
            self._checkout(hold_id=self.client.get(f"/api/purchase-requests/{request_id}",
                                                   name="GET purchase-request").json()["holdId"])

    def _checkout(self, hold_id):
        with self.client.post(f"/api/holds/{hold_id}/confirm", name="POST checkout",
                              catch_response=True) as resp:
            if resp.status_code == 201:
                _record("checkout completed", [s["eventSeatId"] for s in resp.json()["seats"]])
            else:
                resp.failure(f"HTTP {resp.status_code}")

    def _pick_seats(self):
        if not self.available:
            return []
        hot = self.available[:HOT_SEATS] if random.random() < HOT_SHARE else []
        pool = hot or self.available
        return random.sample(pool, min(len(pool), random.randint(*SEATS_PER_ORDER)))

    def _forget(self, seats):
        taken = set(seats)
        self.available = [s for s in self.available if s not in taken]


@events.test_stop.add_listener
def report(environment, **_kwargs):
    with _lock:
        duplicates = [seat for seat, n in Counter(_sold_seats).items() if n > 1]
        outcomes = dict(sorted(_outcomes.items()))
        sold = len(_sold_seats)

    print("\n=== SeatLock outcomes ===")
    for name, count in outcomes.items():
        print(f"  {name:<28} {count}")
    print(f"  seats sold by checkout       {sold}")
    print(f"  seats sold more than once    {len(duplicates)}")

    host = environment.host or "http://localhost:8080"
    try:
        import requests
        seat_map = requests.get(f"{host}/api/events/{EVENT_ID}/seat-map", timeout=30).json()
        booked = sum(1 for sec in seat_map["sections"] for s in sec["seats"] if s["status"] == "BOOKED")
        print(f"  seat map shows BOOKED        {booked}")
    except Exception as ex:  # report only; never fail the run on the summary request
        print(f"  could not read final seat map: {ex}")

    if duplicates:
        print("RESULT: FAIL - a seat was sold twice")
        environment.process_exit_code = 1
    else:
        print("RESULT: PASS - no seat sold twice")
