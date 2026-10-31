// SeatLock web client: the checkout form on the payment page. A guest first gives their email and
// phone number (Your details), then pays by card or UPI and clicks Pay now. Loaded after app.js and
// uses its helpers (el, $, state, money, confirmBooking, cancelCheckout); PHONE_PATTERN is in account.js.
//
// Nothing here takes money. Card and UPI details are checked in the browser and are never sent to
// the server or stored; only the published test details below are accepted, so a real card is
// always refused. A real payment provider would replace processDemoPayment(). Only a guest's email
// and phone are sent, with the booking confirmation.

const TEST_CARDS = {
    "4242424242424242": { approved: true },
    "5555555555554444": { approved: true },
    "4000000000000002": { approved: false, message: "Your card was declined. Try another card." },
    "4000000000009995": { approved: false, message: "Your card has insufficient funds. Try another card." },
};
const TEST_UPI_IDS = {
    "success@demo": { approved: true },
    "failure@demo": { approved: false, message: "The payment was declined in your UPI app. Try again." },
};
const PROCESSING_MS = 1500;           // how long the demo "bank" takes to answer

// One form per hold: the checkout page is redrawn often (busy state, timer), and reusing the same
// form keeps what the buyer has typed.
const payment = { holdId: null, node: null, method: "card", processing: false };

function checkoutForm(hold) {
    if (payment.holdId !== hold.id) {
        payment.holdId = hold.id;
        payment.method = "card";
        payment.processing = false;
        payment.node = buildCheckoutForm();
    }
    updatePaymentCard(hold);
    return payment.node;
}

function buildCheckoutForm() {
    const field = (label, input, extra) => el("label", { class: "field" },
        el("span", { class: "field-label" }, label), input, extra);
    const input = (name, props) => el("input", { name, autocomplete: "off", spellcheck: "false", ...props });

    // Your details: a guest types their email and phone; a signed-in buyer's come from the account.
    const details = state.me
        ? el("div", { class: "card details-card" },
            el("h3", {}, "Your details"),
            el("p", {}, el("strong", {}, state.me.name), el("span", { class: "muted" }, ` · ${state.me.email}`)),
            el("p", { class: "muted small" }, "Your booking details are emailed to this address and saved in My bookings."))
        : el("div", { class: "card details-card" },
            el("h3", {}, "Your details"),
            el("p", { class: "muted small" },
                "We email your booking details to this address. Your phone number is kept with the booking in case we need to reach you about it."),
            field("Email", input("contactEmail", { type: "email", autocomplete: "email", required: true, maxlength: 254,
                placeholder: "you@example.com" })),
            field("Phone number", input("contactPhone", { type: "tel", autocomplete: "tel", required: true, maxlength: 20,
                pattern: PHONE_PATTERN, placeholder: "+44 20 7946 0000" })));

    const cardPanel = el("div", { class: "pay-panel", "data-method": "card" },
        field("Name on card", input("cardName", { placeholder: "As printed on the card", maxlength: 60 })),
        field("Card number", input("cardNumber", { inputmode: "numeric", placeholder: "4242 4242 4242 4242", maxlength: 23,
            oninput: formatCardNumber }), el("span", { class: "card-brand", "aria-live": "polite" })),
        el("div", { class: "field-row" },
            field("Expiry", input("cardExpiry", { inputmode: "numeric", placeholder: "MM/YY", maxlength: 5, oninput: formatExpiry })),
            field("CVC", input("cardCvc", { inputmode: "numeric", placeholder: "123", maxlength: 4 }))),
        el("button", { class: "link-button", type: "button", onclick: fillTestCard }, "Use a test card"));

    const upiPanel = el("div", { class: "pay-panel", "data-method": "upi", hidden: true },
        field("UPI ID", input("upiId", { placeholder: "name@bank", maxlength: 60 })),
        el("p", { class: "muted small" }, "In a real checkout your UPI app would ask you to approve the payment."),
        el("button", { class: "link-button", type: "button", onclick: fillTestUpi }, "Use a test UPI ID"));

    const tabs = el("div", { class: "tabs", role: "tablist", "aria-label": "Payment method" },
        [["card", "Card"], ["upi", "UPI"]].map(([method, label]) => el("button", {
            class: "tab", type: "button", role: "tab", "data-method": method,
            onclick: () => { payment.method = method; showPaymentError(""); updatePaymentCard(state.hold); },
        }, label)));

    const paymentPart = el("div", { class: "card payment-card" },
        el("div", { class: "payment-head" }, el("h3", {}, "Payment"), el("span", { class: "demo-badge" }, "Demo")),
        el("p", { class: "muted small" },
            "No money is taken. Only test details work, and they never leave your browser."),
        el("div", { class: "form pay-fields" },
        tabs, cardPanel, upiPanel,
        el("details", { class: "test-details" },
            el("summary", {}, "Test details"),
            el("ul", {},
                el("li", {}, el("code", {}, "4242 4242 4242 4242"), " or ", el("code", {}, "5555 5555 5555 4444"), ": approved"),
                el("li", {}, el("code", {}, "4000 0000 0000 0002"), ": declined"),
                el("li", {}, el("code", {}, "4000 0000 0000 9995"), ": insufficient funds"),
                el("li", {}, "Any future expiry, any 3-digit CVC, any name"),
                el("li", {}, "UPI: ", el("code", {}, "success@demo"), " approved, ", el("code", {}, "failure@demo"), " declined"))),
        el("p", { class: "form-error", role: "alert" }),
        el("div", { class: "actions" },
            el("button", { class: "btn pay-button", type: "submit" }),
            el("button", { class: "btn danger", type: "button", onclick: cancelCheckout }, "Cancel and go back"))));

    return el("form", { class: "checkout-form pay-form", novalidate: true, onsubmit: submitPayment }, details, paymentPart);
}

function updatePaymentCard(hold) {
    const node = payment.node;
    const locked = state.busy || payment.processing;
    for (const tab of node.querySelectorAll(".tab")) {
        const active = tab.dataset.method === payment.method;
        tab.classList.toggle("active", active);
        tab.setAttribute("aria-selected", String(active));
        tab.disabled = locked;
    }
    for (const panel of node.querySelectorAll(".pay-panel")) panel.hidden = panel.dataset.method !== payment.method;
    for (const control of node.querySelectorAll("input, .link-button, .actions button")) control.disabled = locked;
    const button = node.querySelector(".pay-button");
    button.textContent = payment.processing ? "Processing payment…"
        : state.busy ? "Confirming booking…" : `Pay now · ${money(hold.totalCents)}`;
    button.classList.toggle("processing", locked);
}

function showPaymentError(message) {
    payment.node.querySelector(".form-error").textContent = message;
}

function formValue(name) {
    return payment.node.querySelector(`[name="${name}"]`).value.trim();
}

function setFormValue(name, value) {
    payment.node.querySelector(`[name="${name}"]`).value = value;
}

// ---- typing helpers ------------------------------------------------------

function formatCardNumber(event) {
    const digits = event.target.value.replace(/\D/g, "").slice(0, 19);
    event.target.value = digits.replace(/(.{4})(?=.)/g, "$1 ");
    payment.node.querySelector(".card-brand").textContent = cardBrand(digits);
}

function formatExpiry(event) {
    const digits = event.target.value.replace(/\D/g, "").slice(0, 4);
    event.target.value = digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
}

function cardBrand(digits) {
    if (/^4/.test(digits)) return "Visa";
    if (/^(5[1-5]|2[2-7])/.test(digits)) return "Mastercard";
    if (/^3[47]/.test(digits)) return "American Express";
    if (/^6/.test(digits)) return "RuPay / Discover";
    return "";
}

function fillTestCard() {
    const nextYear = String((new Date().getFullYear() + 1) % 100).padStart(2, "0");
    setFormValue("cardName", state.me?.name || "Demo Buyer");
    setFormValue("cardNumber", "4242 4242 4242 4242");
    setFormValue("cardExpiry", `12/${nextYear}`);
    setFormValue("cardCvc", "123");
    payment.node.querySelector(".card-brand").textContent = "Visa";
    showPaymentError("");
}

function fillTestUpi() {
    setFormValue("upiId", "success@demo");
    showPaymentError("");
}

// ---- checks --------------------------------------------------------------

function passesLuhn(digits) {
    let sum = 0;
    for (let i = 0; i < digits.length; i++) {
        let d = Number(digits[digits.length - 1 - i]);
        if (i % 2 === 1) d = d * 2 > 9 ? d * 2 - 9 : d * 2;
        sum += d;
    }
    return sum % 10 === 0;
}

function expiryIsFuture(text) {
    const match = /^(\d{2})\/(\d{2})$/.exec(text);
    if (!match) return false;
    const month = Number(match[1]);
    if (month < 1 || month > 12) return false;
    const endOfMonth = new Date(2000 + Number(match[2]), month, 1);   // first day of the next month
    return endOfMonth > new Date();
}

// A guest's email and phone: { contact } when both are fine, or { error, field } to point at.
function checkContact() {
    const email = payment.node.querySelector('[name="contactEmail"]');
    const phone = payment.node.querySelector('[name="contactPhone"]');
    if (!email.value.trim() || !email.checkValidity()) {
        return { error: "Enter your email address, so we can send your booking details.", field: email };
    }
    if (!phone.value.trim() || !phone.checkValidity()) {
        return { error: "Enter your phone number, such as +44 20 7946 0000.", field: phone };
    }
    return { contact: { contactEmail: email.value.trim(), contactPhone: phone.value.trim() } };
}

// Returns { error } for a form problem, or { outcome, paidWith } for what the demo bank answers.
function checkCard() {
    const digits = formValue("cardNumber").replace(/\D/g, "");
    if (!formValue("cardName")) return { error: "Enter the name on the card." };
    if (digits.length < 12 || !passesLuhn(digits)) return { error: "That card number is not valid." };
    if (!expiryIsFuture(formValue("cardExpiry"))) return { error: "Enter an expiry date in the future, as MM/YY." };
    if (!/^\d{3,4}$/.test(formValue("cardCvc"))) return { error: "Enter the 3 or 4 digit CVC." };
    const outcome = TEST_CARDS[digits];
    if (!outcome) return { error: "This is a demo checkout, so only test cards work. Try 4242 4242 4242 4242." };
    return { outcome, paidWith: `${cardBrand(digits) || "Card"} ending ${digits.slice(-4)}` };
}

function checkUpi() {
    const id = formValue("upiId").toLowerCase();
    if (!/^[\w.-]{2,}@[a-z]{2,}$/.test(id)) return { error: "Enter a UPI ID like name@bank." };
    const outcome = TEST_UPI_IDS[id];
    if (!outcome) return { error: "This is a demo checkout, so only test UPI IDs work. Try success@demo." };
    return { outcome, paidWith: `UPI (${id})` };
}

// ---- paying --------------------------------------------------------------

function processDemoPayment(outcome) {
    return new Promise(resolve => setTimeout(() => resolve(outcome), PROCESSING_MS));
}

async function submitPayment(event) {
    event.preventDefault();
    if (payment.processing || state.busy || !state.hold) return;
    // Your details first (guests only), then the payment itself.
    const details = state.me ? { contact: null } : checkContact();
    if (details.error) {
        details.field.focus();
        return showPaymentError(details.error);
    }
    const check = payment.method === "card" ? checkCard() : checkUpi();
    if (check.error) return showPaymentError(check.error);

    showPaymentError("");
    payment.processing = true;
    updatePaymentCard(state.hold);
    const outcome = await processDemoPayment(check.outcome);
    payment.processing = false;
    if (!state.hold || payment.holdId !== state.hold.id) return;   // timer ran out or the buyer left meanwhile
    if (!outcome.approved) {
        updatePaymentCard(state.hold);
        return showPaymentError(outcome.message);
    }
    // Forget the card details as soon as they are no longer needed; the contact goes with the booking.
    for (const field of payment.node.querySelectorAll("input")) field.value = "";
    payment.holdId = null;
    await confirmBooking(check.paidWith, details.contact);
}
