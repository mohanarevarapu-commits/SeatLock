// SeatLock web client: Find my tickets (#tickets). A guest enters the email and phone number they
// paid with, gets a 6-digit code by email, and sees their tickets with QR codes. Also the ticket
// card used on the booking confirmation. Loaded after app.js and uses its helpers (el, $, api,
// state, money, dateTime, showPage, typeLabel); PHONE_PATTERN is in account.js.

const RESEND_SECONDS = 30;            // matches TicketAccessService.RESEND_AFTER

const ticketSearch = { step: "details", email: "", phone: "", tickets: [], resendAt: 0, resendTimer: null };
let outboxCheck = null;

// The demo outbox (emails kept when no mail server is set up) only exists in the demo profile and
// only answers this machine, so ask once whether it is there before pointing to it.
function demoOutboxAvailable() {
    outboxCheck ??= fetch("/api/demo/outbox", { headers: { Accept: "application/json" } })
        .then(response => response.ok)
        .catch(() => false);
    return outboxCheck;
}

function showFieldError(error, input, message) {
    error.textContent = message;
    input.focus();
}

async function outboxHint(text) {
    if (!(await demoOutboxAvailable())) return null;
    return el("p", { class: "demo-hint" }, "Demo: ", text, " ",
        el("a", { href: "/outbox.html", target: "_blank", rel: "noopener" }, "Open the demo outbox"), ".");
}

async function renderTickets() {
    const view = $("tickets-view");
    const body = ticketSearch.step === "list" ? ticketList() : ticketSearch.step === "code" ? codeStep() : detailsStep();
    const hint = ticketSearch.step === "code"
        ? await outboxHint("no mail server is set up, so the code is kept in the demo outbox.") : null;
    view.replaceChildren(el("div", { class: "tickets-page" },
        el("div", { class: "card tickets-card" },
            el("h1", {}, "Find my tickets"),
            body,
            hint,
            state.me ? el("p", { class: "muted small" }, "You're logged in: bookings made with your account are under ",
                el("a", { href: "#bookings" }, "My bookings"), ".") : null)));
    showPage("tickets");
    view.querySelector("input")?.focus();
}

function detailsStep() {
    const error = el("p", { class: "form-error", role: "alert" });
    const form = el("form", { class: "form", novalidate: true },
        el("p", { class: "muted" },
            "Booked as a guest? Enter the email and phone number you gave when you paid. We'll email you a 6-digit code."),
        field("Email", el("input", { name: "email", type: "email", autocomplete: "email", required: true, maxlength: 254,
            value: ticketSearch.email, placeholder: "you@example.com" })),
        field("Phone number", el("input", { name: "phone", type: "tel", autocomplete: "tel", required: true, maxlength: 20,
            pattern: PHONE_PATTERN, value: ticketSearch.phone, placeholder: "+44 20 7946 0000" })),
        error,
        el("button", { class: "btn", type: "submit" }, "Send code"));
    form.addEventListener("submit", async event => {
        event.preventDefault();
        const email = form.elements.email;
        const phone = form.elements.phone;
        if (!email.value.trim() || !email.checkValidity()) return showFieldError(error, email, "Enter a valid email address.");
        if (!phone.value.trim() || !phone.checkValidity()) {
            return showFieldError(error, phone, "Enter a phone number, such as +44 20 7946 0000.");
        }
        ticketSearch.email = email.value.trim();
        ticketSearch.phone = phone.value.trim();
        form.querySelector("button").disabled = true;
        try {
            await sendTicketCode();
            ticketSearch.step = "code";
            renderTickets();
        } catch (failure) {
            error.textContent = explain(failure);
            form.querySelector("button").disabled = false;
        }
    });
    return form;
}

async function sendTicketCode() {
    await api("/api/tickets/code", {
        method: "POST",
        body: JSON.stringify({ email: ticketSearch.email, phone: ticketSearch.phone }),
    });
    ticketSearch.resendAt = Date.now() + RESEND_SECONDS * 1000;
}

function codeStep() {
    const error = el("p", { class: "form-error", role: "alert" });
    const resend = el("button", { class: "link-button", type: "button" });
    const form = el("form", { class: "form", novalidate: true },
        el("p", { class: "muted" }, "If there are bookings for ", el("strong", {}, ticketSearch.email), " and ",
            el("strong", {}, ticketSearch.phone), ", we've emailed a 6-digit code. It works once, for 10 minutes."),
        field("Code", el("input", { name: "code", inputmode: "numeric", autocomplete: "one-time-code", required: true,
            maxlength: 6, pattern: "\\d{6}", placeholder: "123456", class: "code-input" })),
        error,
        el("button", { class: "btn", type: "submit" }, "View my tickets"),
        el("div", { class: "code-links" },
            resend,
            el("button", { class: "link-button", type: "button",
                onclick: () => { ticketSearch.step = "details"; renderTickets(); } }, "Use different details")));
    form.addEventListener("submit", async event => {
        event.preventDefault();
        const code = form.elements.code;
        if (!/^\d{6}$/.test(code.value.trim())) return showFieldError(error, code, "Enter the 6-digit code from the email.");
        form.querySelector(".btn").disabled = true;
        try {
            ticketSearch.tickets = await api("/api/tickets/lookup", {
                method: "POST",
                body: JSON.stringify({ email: ticketSearch.email, phone: ticketSearch.phone, code: code.value.trim() }),
            });
            ticketSearch.step = "list";
            renderTickets();
        } catch (failure) {
            error.textContent = explain(failure);
            form.querySelector(".btn").disabled = false;
            code.select();
        }
    });
    resend.addEventListener("click", async () => {
        try {
            await sendTicketCode();
            error.textContent = "";
            toast("If there are bookings for these details, a new code is on its way.");
        } catch (failure) {
            error.textContent = explain(failure);
        }
        updateResend(resend);
    });
    updateResend(resend);
    return form;
}

// "Send a new code" waits out the same 30 seconds the server does.
function updateResend(button) {
    clearInterval(ticketSearch.resendTimer);
    const tick = () => {
        const left = Math.ceil((ticketSearch.resendAt - Date.now()) / 1000);
        button.disabled = left > 0;
        button.textContent = left > 0 ? `Send a new code (${left}s)` : "Send a new code";
        if (left <= 0) clearInterval(ticketSearch.resendTimer);
    };
    tick();
    ticketSearch.resendTimer = setInterval(tick, 1000);
}

function ticketList() {
    const now = Date.now();
    const upcoming = ticketSearch.tickets.filter(t => new Date(t.startsAt) > now);
    const past = ticketSearch.tickets.filter(t => new Date(t.startsAt) <= now);
    return el("div", { class: "ticket-results" },
        el("p", { class: "muted" }, `Tickets booked with ${ticketSearch.email}:`),
        ticketSearch.tickets.length === 0 ? el("p", { class: "muted" }, "No tickets found for these details.") : null,
        upcoming.map(t => ticketCard(t, false)),
        past.length > 0 ? el("h2", { class: "past-heading" }, "Past concerts") : null,
        past.map(t => ticketCard(t, true)),
        el("button", { class: "btn secondary", type: "button", onclick: () => {
            Object.assign(ticketSearch, { step: "details", tickets: [] });
            renderTickets();
        } }, "Look up other tickets"));
}

// One ticket: concert, QR code, venue, seats and total. Also used on the booking confirmation.
function ticketCard(ticket, past = false) {
    return el("article", { class: "ticket" + (past ? " past" : "") },
        el("div", { class: "ticket-main" },
            el("span", { class: "badge-ticket" }, past ? "Past" : "Ticket"),
            el("h3", {}, ticket.eventName),
            el("p", { class: "ticket-when" }, dateTime(ticket.startsAt)),
            el("p", { class: "ticket-venue" },
                el("strong", {}, ticket.venueName), el("br"),
                ticket.venueAddress ? [ticket.venueAddress, el("br")] : null,
                ticket.venueCity, el("br"),
                el("span", { class: "muted" }, `${typeLabel(ticket.venueType)} · ${ticket.stageLabel}`)),
            el("ul", { class: "cart-lines" }, ticket.seats.map(s => el("li", {},
                el("span", {}, `${s.section} · Row ${s.row} · Seat ${s.number}`), el("span", {}, money(s.priceCents))))),
            el("div", { class: "total" }, el("span", {}, "Total"), el("span", {}, money(ticket.totalCents)))),
        el("div", { class: "ticket-qr" },
            el("img", { src: `/api/bookings/${encodeURIComponent(ticket.reference)}/qr`, width: 160, height: 160,
                alt: `QR code for booking ${ticket.reference}` }),
            el("span", { class: "ref" }, ticket.reference),
            el("span", { class: "muted small" }, "Show at the entrance")));
}
