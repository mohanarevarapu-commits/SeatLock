// SeatLock web client: find a show on the home page, select seats, book (seats are kept 10 minutes), then pay.
// Talks only to the public REST API, the same one documented at /swagger-ui.html.

const MAX_SEATS = 8;                  // matches seatlock.holds.max-seats-per-hold
const REFRESH_MS = 5000;              // how often the seat map is reloaded
const QUEUE_POLL_MS = 500;
const QUEUE_MAX_POLLS = 60;

const state = {
    events: [],
    event: null,
    seatEls: new Map(),               // eventSeatId -> seat button
    seatInfo: new Map(),              // eventSeatId -> { label, section, priceCents, status }
    selected: new Set(),
    hold: null,
    busy: false,
    refreshTimer: null,
    countdownTimer: null,
    query: "",
    eventsLoadedAt: 0,
    homeList: null,                   // the home block that is open (a HOME_LISTS key), or null for the blocks
    zoom: 1,
    me: null,                         // the signed-in account, or null for a guest (see account.js)
    resumeBooking: null,              // "book" or "queue" to continue after logging in from Book now
};

const customerRef = loadCustomerRef();

// ---- small helpers -------------------------------------------------------

function $(id) { return document.getElementById(id); }

function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (key === "class") node.className = value;
        else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
        else if (value !== false && value != null) node.setAttribute(key, value === true ? "" : value);
    }
    node.append(...children.flat().filter(c => c != null));
    return node;
}

function money(cents) {
    return "$" + (cents / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function dateTime(iso) {
    return new Date(iso).toLocaleString(undefined, { dateStyle: "full", timeStyle: "short" });
}

function storage(action, key, value) {
    try {
        if (action === "get") return localStorage.getItem(key);
        if (action === "set") localStorage.setItem(key, value);
        if (action === "remove") localStorage.removeItem(key);
    } catch { /* storage blocked: the app still works, it just forgets on reload */ }
    return null;
}

function loadCustomerRef() {
    let ref = storage("get", "seatlock.customerRef");
    if (!ref) {
        const bytes = crypto.getRandomValues(new Uint8Array(8));
        ref = "web-" + Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
        storage("set", "seatlock.customerRef", ref);
    }
    return ref;
}

let toastTimer;
function toast(message, isError = false) {
    const node = $("toast");
    node.textContent = message;
    node.className = "toast" + (isError ? " error" : "");
    node.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { node.hidden = true; }, 5000);
}

class ApiError extends Error {
    constructor(status, problem) {
        super(problem.detail || problem.title || `HTTP ${status}`);
        this.status = status;
        this.problem = problem;
    }
}

function cookie(name) {
    const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
    return match ? decodeURIComponent(match[1]) : null;
}

async function api(path, options = {}) {
    const headers = { "Content-Type": "application/json", Accept: "application/json" };
    // Signed-in changes must prove they come from this page: echo the CSRF cookie in a header.
    const csrf = cookie("XSRF-TOKEN");
    if (csrf && options.method && options.method !== "GET") headers["X-XSRF-TOKEN"] = csrf;
    const response = await fetch(path, { ...options, headers });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new ApiError(response.status, body);
    return body;
}

function explain(error) {
    if (!(error instanceof ApiError)) return "Could not reach SeatLock. Is the server running?";
    if (error.status === 429) return "Too many requests. Please wait a second and try again.";
    return error.message;
}

// ---- home page -----------------------------------------------------------

const SELLING_FAST_SHARE = 0.4;       // a concert is selling fast once this share of its seats is gone

async function loadEvents() {
    try {
        state.events = await api("/api/events");
        state.eventsLoadedAt = Date.now();
    } catch (error) {
        $("home-sections").replaceChildren(el("p", { class: "muted" }, explain(error)));
        return;
    }
    if (!$("home-view").hidden) renderHome();
}

// The home page shows three blocks; each opens its own list (#today, #tomorrow, #selling-fast),
// and #all lists every concert on sale. The address keeps the browser's Back button working.
const HOME_LISTS = {
    today: {
        title: "Today's concerts",
        hint: "Tonight's line-up",
        empty: "No more concerts today.",
        pick: onSale => onSale.filter(e => isSameDay(e.startsAt, dayFromNow(0))),
    },
    tomorrow: {
        title: "Tomorrow's concerts",
        hint: "Plan your night out",
        empty: "No concerts tomorrow.",
        pick: onSale => onSale.filter(e => isSameDay(e.startsAt, dayFromNow(1))),
    },
    "selling-fast": {
        title: "Selling fast",
        hint: "Over 40% of seats gone",
        empty: "Nothing is close to selling out.",
        pick: onSale => onSale.filter(e => e.availableSeats > 0 && soldShare(e) >= SELLING_FAST_SHARE)
            .sort((a, b) => soldShare(b) - soldShare(a)),
        sold: true,
    },
    all: {
        title: "All concerts",
        empty: "No concerts on sale.",
        pick: onSale => onSale,
    },
};

function homeListOf(hash) {
    const key = hash.slice(1);
    return Object.hasOwn(HOME_LISTS, key) ? key : null;
}

function showHome(list = null) {
    state.event = null;
    state.homeList = list;
    clearInterval(state.refreshTimer);
    showPage("home");
    renderHome();
    // Seat counts move on while the buyer looks at a show; refresh them unless they are brand new.
    if (Date.now() - (state.eventsLoadedAt || 0) > 5000) loadEvents();
}

function typeLabel(type) {
    const text = type.replaceAll("_", " ").toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
}

function soldShare(event) {
    return event.totalSeats ? 1 - event.availableSeats / event.totalSeats : 0;
}

function isSameDay(iso, day) {
    return new Date(iso).toDateString() === day.toDateString();
}

function matchesSearch(event) {
    const query = state.query.trim().toLowerCase();
    return !query || [event.name, event.venueName, event.venueCity].some(t => t.toLowerCase().includes(query));
}

// "Only 12 left" once a show is nearly gone, so buyers see it without opening the seat map.
function availabilityNote(event) {
    if (event.availableSeats === 0) return el("span", { class: "note hot" }, "Sold out");
    if (event.availableSeats <= Math.max(20, event.totalSeats * 0.1)) {
        return el("span", { class: "note hot" }, `Only ${event.availableSeats} left`);
    }
    return null;
}

function dayFromNow(days) {
    const day = new Date();
    day.setDate(day.getDate() + days);
    return day;
}

function renderHome() {
    const box = $("home-sections");
    if (state.events.length === 0) {
        return box.replaceChildren(el("p", { class: "muted" },
            "No concerts yet. Start the app with the demo profile to get some."));
    }
    const now = Date.now();
    const onSale = state.events.filter(e => new Date(e.startsAt) > now);

    // Searching: one plain list of what matches.
    if (state.query.trim()) {
        const shows = onSale.filter(matchesSearch);
        return box.replaceChildren(listSection(`${shows.length} ${shows.length === 1 ? "concert" : "concerts"}`, shows,
            "Nothing matches. Try another concert, venue or city.", false, false));
    }

    // A block was opened: its concerts, with details.
    if (state.homeList) {
        const list = HOME_LISTS[state.homeList];
        return box.replaceChildren(listSection(list.title, list.pick(onSale), list.empty, list.sold, true));
    }

    // The start page: three blocks, no concert details.
    const blocks = ["today", "tomorrow", "selling-fast"].map(key => {
        const list = HOME_LISTS[key];
        const count = list.pick(onSale).length;
        return el("a", { class: "home-block" + (key === "selling-fast" ? " hot" : ""), href: `#${key}` },
            el("span", { class: "block-title" }, list.title),
            el("strong", { class: "block-count" }, String(count)),
            el("span", { class: "muted" }, count === 1 ? "concert" : "concerts"),
            el("span", { class: "block-hint" }, list.hint, el("span", { "aria-hidden": "true" }, " →")));
    });
    box.replaceChildren(
        el("div", { class: "home-blocks" }, blocks),
        el("div", { class: "browse-all" },
            el("a", { class: "btn secondary", href: "#all" }, `Browse all ${onSale.length} concerts`)));
}

function listSection(title, shows, emptyText, sold, withBack) {
    return el("section", { class: "home-section" },
        el("div", { class: "list-head" }, el("h2", {}, title),
            withBack ? el("a", { class: "back-link", href: "#" }, "← Back") : null),
        shows.length > 0 ? showList(shows, sold) : el("p", { class: "muted" }, emptyText));
}

function showList(shows, sold = false) {
    return el("ul", { class: "show-list" }, shows.map(event => {
        const date = new Date(event.startsAt);
        return el("li", {}, el("a", { class: "show-row", href: `#event-${event.id}` },
            el("span", { class: "date-tile" },
                el("span", {}, date.toLocaleString(undefined, { month: "short" })),
                el("strong", {}, String(date.getDate()))),
            el("span", { class: "show-main" },
                el("strong", {}, event.name),
                el("span", { class: "muted" },
                    `${event.venueName}, ${event.venueCity} · ${date.toLocaleString(undefined,
                        { weekday: "short", hour: "2-digit", minute: "2-digit" })}`)),
            el("span", { class: "show-side" },
                sold ? el("span", { class: "note hot" }, `${Math.round(soldShare(event) * 100)}% sold`)
                    : availabilityNote(event) || el("span", { class: "muted small" }, typeLabel(event.venueType)),
                el("span", {}, `From ${money(event.fromPriceCents)}`))));
    }));
}

$("search").addEventListener("input", event => {
    state.query = event.target.value;
    renderHome();
});

// ---- one show ------------------------------------------------------------

function openEvent(event) {
    showPage("browse");
    selectEvent(event);
}

async function selectEvent(event) {
    if (state.event?.id === event.id) return;
    state.event = event;
    state.selected.clear();
    $("event-name").textContent = event.name;
    $("event-meta").textContent =
        `${event.venueName}, ${event.venueCity} · ${typeLabel(event.venueType)} · ${dateTime(event.startsAt)}`;
    $("seat-map").replaceChildren(el("p", { class: "muted" }, "Loading seats…"));
    state.seatEls.clear();
    await refreshSeatMap();
    renderCart();
    clearInterval(state.refreshTimer);
    state.refreshTimer = setInterval(refreshSeatMap, REFRESH_MS);
}

// ---- seat map ------------------------------------------------------------

async function refreshSeatMap() {
    const event = state.event;
    if (!event) return;
    let map;
    try {
        map = await api(`/api/events/${event.id}/seat-map`);
    } catch (error) {
        toast(explain(error), true);
        return;
    }
    if (state.event?.id !== event.id) return;   // user switched shows meanwhile

    if (state.seatEls.size === 0) buildSeatMap(map);
    showSeatStatus(map);

    let lostSelection = 0;
    for (const section of map.sections) {
        for (const seat of section.seats) {
            const info = state.seatInfo.get(seat.eventSeatId);
            if (info) info.status = seat.status;
            if (seat.status !== "AVAILABLE" && state.selected.delete(seat.eventSeatId)) lostSelection++;
        }
    }
    if (lostSelection > 0) {
        toast(`${lostSelection === 1 ? "A seat" : lostSelection + " seats"} you picked just went to someone else.`, true);
        renderCart();
    }
    paintSeats();
}

// The live seat status above the map: free, held and sold counts, refreshed with the map.
function showSeatStatus(map) {
    const counts = { AVAILABLE: 0, HELD: 0, BOOKED: 0 };
    for (const section of map.sections) {
        for (const seat of section.seats) counts[seat.status]++;
    }
    const total = counts.AVAILABLE + counts.HELD + counts.BOOKED;
    $("available-count").textContent = counts.AVAILABLE === 0 ? "Sold out"
        : `${counts.AVAILABLE.toLocaleString()} of ${total.toLocaleString()} seats free`;
    $("available-count").classList.toggle("hot", counts.AVAILABLE <= Math.max(20, total * 0.1));
    $("count-free").textContent = counts.AVAILABLE.toLocaleString();
    $("count-held").textContent = counts.HELD.toLocaleString();
    $("count-sold").textContent = counts.BOOKED.toLocaleString();
}

// The venue is drawn on a 3x3 grid, north at the top. The stage takes one cell (an end stage is
// NORTH, a stage in the round is CENTER) and each section sits in the cell the API gives it.
const ZONES = ["NORTH_WEST", "NORTH", "NORTH_EAST", "WEST", "CENTER", "EAST", "SOUTH_WEST", "SOUTH", "SOUTH_EAST"];

function cellOf(zone) {
    const i = ZONES.indexOf(zone);
    return { col: i % 3, row: Math.floor(i / 3) };
}

// Seats face the stage: rows run across when the stage is straight above or below, and up and down
// when it is to the side, so side stands and opera boxes face inward. Row A is always nearest the stage.
function facing(cell, stage) {
    const dx = stage.col - cell.col;
    const dy = stage.row - cell.row;
    if (Math.abs(dy) > Math.abs(dx)) return dy < 0 ? "up" : "down";
    return dx < 0 ? "left" : "right";
}

// Pull each block towards the stage inside its grid cell.
function hug(delta) {
    return delta > 0 ? "end" : delta < 0 ? "start" : "center";
}

function placeIn(node, { col, row }) {
    node.style.gridArea = `${row + 1} / ${col + 1}`;
    return node;
}

function buildSeatMap(map) {
    state.seatInfo.clear();
    const stage = cellOf(map.stageZone);
    const grid = el("div", { class: "venue-grid" });

    const shape = map.stageZone === "CENTER" ? "center" : stage.col === 1 ? "end" : stage.row === 1 ? "side" : "corner";
    const onField = shape === "center" && (map.venueType === "STADIUM" || map.venueType === "GROUND");
    grid.append(placeIn(el("div", { class: `stage-block ${shape} ${map.stageZone.toLowerCase()}${onField ? " field" : ""}` },
        el("span", {}, map.stageLabel)), stage));

    const byZone = new Map();
    for (const section of map.sections) {
        if (!byZone.has(section.zone)) byZone.set(section.zone, []);
        byZone.get(section.zone).push(section);
    }
    for (const [zone, sections] of byZone) {
        const cell = cellOf(zone);
        const face = facing(cell, stage);
        const block = el("div", { class: "zone" }, sections.map(s => sectionBlock(s, face)));
        // Several sections in one zone: tiers stack away from the stage (stalls, then circle, then
        // gallery); beside the stage they line up along the wall from the stage end, like opera boxes.
        block.style.flexDirection = face === "up" ? "column" : face === "down" ? "column-reverse"
            : stage.row > cell.row ? "column-reverse" : "column";
        block.style.justifySelf = hug(stage.col - cell.col);
        block.style.alignSelf = hug(stage.row - cell.row);
        grid.append(placeIn(block, cell));
    }
    $("seat-map").replaceChildren(grid);
    fitSeatMap();
}

function sectionBlock(section, face) {
    const rows = new Map();
    for (const seat of section.seats) {
        if (!rows.has(seat.row)) rows.set(seat.row, []);
        rows.get(seat.row).push(seat);
        state.seatInfo.set(seat.eventSeatId, {
            label: seat.label, section: section.name, priceCents: section.priceCents, status: seat.status,
        });
    }
    const rowEls = [...rows].map(([row, seats]) => el("div", { class: "row" },
        el("span", { class: "row-label" }, row),
        seats.map(seat => {
            const button = el("button", {
                class: "seat",
                type: "button",
                "data-id": seat.eventSeatId,
                title: `${section.name} ${seat.label} · ${money(section.priceCents)}`,
                "aria-label": `${section.name} seat ${seat.label}, ${money(section.priceCents)}`,
            });
            state.seatEls.set(seat.eventSeatId, button);
            return button;
        })));
    return el("div", { class: "section" },
        el("div", { class: "section-head" },
            el("strong", {}, section.name),
            el("span", { class: "muted" }, money(section.priceCents))),
        el("div", { class: `rows face-${face}` }, rowEls));
}

// ---- zoom ----------------------------------------------------------------

function setZoom(zoom) {
    const grid = $("seat-map").firstElementChild;
    if (!grid) return;
    state.zoom = Math.min(2, Math.max(0.3, zoom));
    grid.style.zoom = state.zoom;
    $("zoom-level").textContent = `${Math.round(state.zoom * 100)}%`;
}

// Shrink big venues so the whole map is visible at once; small ones stay at full size.
function fitSeatMap() {
    const grid = $("seat-map").firstElementChild;
    // A hidden map (behind the log-in or payment page) has no size yet; fit it when it is shown.
    if (!grid || $("seat-map").clientWidth === 0) return;
    grid.style.zoom = 1;
    setZoom(Math.min(1, $("seat-map").clientWidth / grid.scrollWidth));
}

$("zoom-in").addEventListener("click", () => setZoom(state.zoom + 0.15));
$("zoom-out").addEventListener("click", () => setZoom(state.zoom - 0.15));
$("zoom-fit").addEventListener("click", fitSeatMap);

function paintSeats() {
    const mine = new Set(state.hold?.status === "ACTIVE" ? state.hold.seats.map(s => s.eventSeatId) : []);
    for (const [id, button] of state.seatEls) {
        const status = state.seatInfo.get(id).status;
        let cls = "available";
        if (mine.has(id)) cls = "mine";
        else if (state.selected.has(id)) cls = "selected";
        else if (status === "HELD") cls = "held";
        else if (status === "BOOKED") cls = "booked";
        const className = "seat " + cls;
        if (button.className !== className) button.className = className;
        button.disabled = cls !== "available" && cls !== "selected" || state.hold != null;
    }
}

$("seat-map").addEventListener("click", event => {
    const button = event.target.closest("button.seat");
    if (!button || button.disabled) return;
    const id = Number(button.dataset.id);
    if (state.selected.has(id)) {
        state.selected.delete(id);
    } else if (state.selected.size >= MAX_SEATS) {
        toast(`You can hold up to ${MAX_SEATS} seats at a time.`, true);
        return;
    } else {
        state.selected.add(id);
    }
    paintSeats();
    renderCart();
});

// ---- cart ----------------------------------------------------------------

function renderCart() {
    const cart = $("cart");
    const seats = [...state.selected].map(id => state.seatInfo.get(id)).filter(Boolean);
    if (seats.length === 0) {
        return cart.replaceChildren(
            el("h3", {}, "Your seats"),
            el("p", { class: "muted" }, `Click seats on the map to select them (up to ${MAX_SEATS}).`));
    }
    const total = seats.reduce((sum, s) => sum + s.priceCents, 0);
    cart.replaceChildren(
        el("h3", {}, `Your seats (${seats.length})`),
        seatLines(seats.map(s => ({ section: s.section, label: s.label, priceCents: s.priceCents }))),
        el("div", { class: "total" }, el("span", {}, "Total"), el("span", {}, money(total))),
        el("div", { class: "actions" },
            el("button", { class: "btn", type: "button", disabled: state.busy,
                onclick: () => askHowToBook("book") }, "Book now"),
            el("p", { class: "muted small" }, "Your seats are kept for 10 minutes while you pay."),
            el("button", { class: "btn secondary", type: "button", disabled: state.busy,
                onclick: () => askHowToBook("queue"),
                title: "Uses the queued checkout meant for peak traffic" }, "Join the queue instead"),
            el("button", { class: "btn secondary", type: "button", disabled: state.busy,
                onclick: () => { state.selected.clear(); paintSeats(); renderCart(); } }, "Clear selection")));
}

function seatLines(seats) {
    return el("ul", { class: "cart-lines" }, seats.map(s =>
        el("li", {}, el("span", {}, `${s.section} · ${s.label}`), el("span", {}, money(s.priceCents)))));
}

function bookingView(booking) {
    return [
        el("p", { class: "muted" }, "Booking reference"),
        el("div", { class: "ticket-ref" }, booking.reference),
        seatLines(booking.seats),
        el("div", { class: "total" }, el("span", {}, "Paid"), el("span", {}, money(booking.totalCents))),
    ];
}

async function withBusy(action) {
    if (state.busy) return;
    state.busy = true;
    renderCart();
    try {
        await action();
    } finally {
        state.busy = false;
        renderCart();
        paintSeats();
        if (state.hold) renderCheckout();
    }
}

// Book now reserves the seats (a hold) and opens the payment page. The hold is what stops anyone
// else taking the seats while this buyer pays; if payment is not done in time it lapses on the
// server and the seats go back on sale. askHowToBook (account.js) decides first whether the buyer
// logs in or continues as a guest.
function holdRequest() {
    return JSON.stringify({ customerRef, eventSeatIds: [...state.selected] });
}

function bookNow() {
    return withBusy(async () => {
        try {
            const hold = await api(`/api/events/${state.event.id}/holds`, {
                method: "POST",
                body: holdRequest(),
            });
            state.selected.clear();
            openCheckout(hold);
        } catch (error) {
            handleSeatConflict(error);
            await refreshSeatMap();
        }
    });
}

function handleSeatConflict(error) {
    const taken = error instanceof ApiError ? error.problem.eventSeatIds : null;
    if (Array.isArray(taken) && taken.length > 0) {
        const labels = taken.map(id => state.seatInfo.get(id)?.label).filter(Boolean);
        taken.forEach(id => state.selected.delete(id));
        toast(`Sorry, ${labels.join(", ")} ${labels.length === 1 ? "was" : "were"} just taken. Pick other seats.`, true);
    } else {
        toast(explain(error), true);
    }
}

function queueSeats() {
    return withBusy(async () => {
        let request;
        try {
            request = await api(`/api/events/${state.event.id}/purchase-requests`, {
                method: "POST",
                body: holdRequest(),
            });
        } catch (error) {
            toast(explain(error), true);
            return;
        }
        toast("You're in the queue…");
        for (let i = 0; i < QUEUE_MAX_POLLS; i++) {
            await new Promise(resolve => setTimeout(resolve, QUEUE_POLL_MS));
            let status;
            try {
                status = await api(`/api/purchase-requests/${request.requestId}`);
            } catch (error) {
                continue;
            }
            if (status.status === "QUEUED") continue;
            state.selected.clear();
            if (status.status === "HELD") {
                openCheckout(await api(`/api/holds/${status.holdId}`));
            } else {
                toast(status.reason || "The queue could not get those seats.", true);
                await refreshSeatMap();
            }
            return;
        }
        toast("Still waiting in the queue. Check back in a moment.", true);
    });
}

// ---- payment page --------------------------------------------------------

function showPage(page) {
    $("home-view").hidden = page !== "home";
    $("browse-view").hidden = page !== "browse";
    $("checkout-view").hidden = page !== "checkout";
    $("auth-view").hidden = page !== "auth";
    $("account-view").hidden = page !== "account";
    $("info-view").hidden = page !== "info";
    if (page === "browse") fitSeatMap();
    window.scrollTo(0, 0);
}

function openCheckout(hold) {
    setHold(hold);
    history.pushState(null, "", "#checkout");
    renderCheckout();
    showPage("checkout");
}

function renderCheckout() {
    const hold = state.hold;
    const event = state.events.find(e => e.id === hold.eventId);
    $("checkout-view").replaceChildren(el("div", { class: "checkout" },
        el("div", { class: "card timer-card" },
            el("p", { class: "muted" }, "Your seats are reserved for"),
            el("div", { id: "timer", class: "timer" }, formatRemaining(hold)),
            el("p", { class: "muted small" },
                "Complete payment before the timer ends, or the seats go back on sale and you return to the seat map.")),
        el("div", { class: "card" },
            el("h3", {}, "Order summary"),
            el("p", {}, el("strong", {}, event.name)),
            el("p", { class: "muted" }, `${event.venueName}, ${event.venueCity} · ${dateTime(event.startsAt)}`),
            seatLines(hold.seats),
            el("div", { class: "total" }, el("span", {}, "Total"), el("span", {}, money(hold.totalCents)))),
        checkoutForm(hold)));           // Your details and the demo card / UPI payment, in payment.js
}

function renderConfirmation(booking, paidWith, contact) {
    const event = state.events.find(e => e.id === booking.eventId);
    $("checkout-view").replaceChildren(el("div", { class: "checkout" },
        el("div", { class: "card confirmation" },
            el("h2", {}, "Booking confirmed 🎉"),
            el("p", { class: "muted" }, `${event.name} · ${event.venueName} · ${dateTime(event.startsAt)}`),
            ...bookingView(booking),
            paidWith ? el("p", { class: "muted small" }, `Paid with ${paidWith} · demo payment, no money was taken`) : null,
            state.me ? el("p", { class: "muted small" },
                `This booking is saved under My bookings, and the details are emailed to ${state.me.email}.`)
                : contact ? el("p", { class: "muted small" },
                    `Booking details are emailed to ${contact.contactEmail}. Keep the reference to look the booking up later.`)
                    : null,
            state.me ? el("a", { class: "btn secondary", href: "#bookings" }, "View my bookings") : null,
            el("a", { class: "btn", href: "#" }, "Back to shows"))));
}

function formatRemaining(hold) {
    const ms = Math.max(0, new Date(hold.expiresAt) - Date.now());
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor(ms / 1000) % 60;
    return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function setHold(hold) {
    state.hold = hold;
    clearInterval(state.countdownTimer);
    if (hold) {
        storage("set", "seatlock.holdId", hold.id);
        state.countdownTimer = setInterval(tickCountdown, 1000);
    } else {
        storage("remove", "seatlock.holdId");
    }
}

function tickCountdown() {
    const timer = $("timer");
    if (!state.hold || !timer) return;
    const remaining = new Date(state.hold.expiresAt) - Date.now();
    timer.textContent = formatRemaining(state.hold);
    timer.classList.toggle("low", remaining < 60_000);
    if (remaining <= 0) {
        // The server has already let the hold lapse, so the seats are free again; just go back.
        setHold(null);
        backToSeatMap();
        toast("Time's up. Your seats were released; pick seats again to book.", true);
    }
}

function backToSeatMap() {
    if (state.event) history.replaceState(null, "", `#event-${state.event.id}`);
    showPage("browse");
    renderCart();
    refreshSeatMap();
}

// Called by the payment form (payment.js) once the demo payment is approved: books the held seats.
// A guest's { contactEmail, contactPhone } go with it; the confirmation email is sent there.
async function confirmBooking(paidWith, contact = null) {
    if (state.busy) return;
    state.busy = true;
    renderCheckout();
    let booking;
    try {
        booking = await api(`/api/holds/${state.hold.id}/confirm`, {
            method: "POST",
            body: contact ? JSON.stringify(contact) : undefined,
        });
    } catch (error) {
        state.busy = false;
        if (error instanceof ApiError && (error.status === 410 || error.status === 409)) {
            setHold(null);
            backToSeatMap();
            toast(error.status === 410 ? "Time's up. Your seats were released; pick seats again to book."
                : explain(error), true);
        } else {
            toast(explain(error), true);
            renderCheckout();
        }
        return;
    }
    state.busy = false;
    setHold(null);
    history.replaceState(null, "", `#event-${booking.eventId}`);
    renderConfirmation(booking, paidWith, contact);
}

async function cancelCheckout() {
    const hold = state.hold;
    setHold(null);
    backToSeatMap();
    if (!hold) return;
    try {
        await api(`/api/holds/${hold.id}`, { method: "DELETE" });
        toast("Booking cancelled. Your seats are back on sale.");
    } catch (error) {
        toast(explain(error), true);
    }
    refreshSeatMap();
}

// The browser's Back button on the payment page cancels the booking, like the Cancel button.
// Moving to an account page instead is handled by route() in account.js.
window.addEventListener("popstate", () => {
    if (location.hash.startsWith("#event-")) {
        if (state.hold) cancelCheckout();
        else if (!$("checkout-view").hidden) backToSeatMap();
    } else if ((location.hash === "" || homeListOf(location.hash) || isInfoRoute(location.hash)) && state.hold) {
        releaseHoldBeforeLeaving();   // off to the home page, a list or a help page; route() shows it
    }
});

async function restoreHold(holdId) {
    try {
        const hold = await api(`/api/holds/${holdId}`);
        if (hold.status !== "ACTIVE" || new Date(hold.expiresAt) <= Date.now()) {
            storage("remove", "seatlock.holdId");
            return false;
        }
        const event = state.events.find(e => e.id === hold.eventId);
        if (!event) return false;
        // Back from the payment page then lands on this show's seat map.
        history.replaceState(null, "", `#event-${event.id}`);
        openEvent(event);
        openCheckout(hold);
        return true;
    } catch {
        storage("remove", "seatlock.holdId");
        return false;
    }
}

// ---- booking lookup ------------------------------------------------------

$("lookup").addEventListener("submit", async event => {
    event.preventDefault();
    const reference = $("lookup-ref").value.trim().toUpperCase();
    const result = $("lookup-result");
    try {
        const booking = await api(`/api/bookings/${encodeURIComponent(reference)}`);
        result.replaceChildren(...bookingView(booking));
    } catch (error) {
        result.replaceChildren(el("p", { class: "muted" },
            error instanceof ApiError && error.status === 404 ? `No booking with reference ${reference}.` : explain(error)));
    }
});

// Start-up is in account.js, which loads after this file.
