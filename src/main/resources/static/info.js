// SeatLock web client: the help and policy pages linked from the footer (#help, #terms, #privacy,
// #security, #payments). Loaded after app.js and uses its helpers (el, $, showPage).
// Every statement here describes what SeatLock actually does; keep it true when behaviour changes.

const INFO_PAGES = {
    help: {
        title: "Help & support",
        intro: "Answers to the questions buyers ask most.",
        sections: [
            ["How do I book?", "Open a concert, click the seats you want on the map (up to 8), then Book now. Pay on the next page and you get a booking reference straight away."],
            ["How long are my seats kept?", "10 minutes from Book now. The timer on the payment page shows how long is left. While it runs, nobody else can take those seats."],
            ["What if the timer runs out?", "The seats go back on sale and you return to the seat map. Pick seats again to book."],
            ["Where are my tickets?", "Your booking reference is shown when you pay. Use Find a booking on any concert page to look it up, or sign in and open My bookings to see all bookings made while signed in."],
            ["I forgot my password", "Use Forgot password on the log-in page. We email a link that works once, for 30 minutes."],
            ["Can I cancel a paid booking?", "Not online yet. You can cancel before paying with Cancel and go back, which puts the seats back on sale at once."],
            ["Do I need an account?", "No. Guests can browse and book. An account just keeps your bookings together in My bookings."],
        ],
    },
    terms: {
        title: "Terms of use",
        intro: "The short version of how SeatLock works. SeatLock is a demo, so bookings are not real tickets.",
        sections: [
            ["Bookings", "A booking is made when you pay before the 10-minute timer ends. Up to 8 seats can be booked at a time. Each booking gets a reference."],
            ["Payment", "The checkout is a demo: Pay completes the booking without taking any money, and no card details are asked for."],
            ["Fair use", "To keep busy on-sales fair, checkout and sign-in requests are limited per IP address, and the queued checkout may be used at peak times. Automated bulk buying is not allowed."],
            ["Accounts", "You are responsible for keeping your password private. Bookings made while signed in can only be seen and paid for from your account."],
            ["Changes", "These terms may change as SeatLock grows; the version on this page applies."],
        ],
    },
    privacy: {
        title: "Your data & privacy",
        intro: "We keep as little about you as we can.",
        sections: [
            ["Booking as a guest", "No name, email or phone number is asked for. Your browser keeps a random reference so it can find your seats again; we store only that reference with the booking."],
            ["With an account", "We store your name, email address and, if you give it, your phone number, plus the bookings you make while signed in. You can change your name and phone in Profile."],
            ["What we never store", "Card or bank details. Your password itself: only a one-way hash of it is kept."],
            ["Emails", "We email you only about your account: a welcome message, password reset links and a notice when your password changes. Email contents are never written to our logs."],
            ["Deleting your account", "Not available online yet."],
        ],
    },
    security: {
        title: "Security",
        intro: "How SeatLock protects your account and your seats.",
        sections: [
            ["Passwords", "Stored only as BCrypt hashes, never in readable form. Wrong passwords and unknown emails get the same answer, so nobody can find out who has an account."],
            ["Password reset links", "Random, single-use and valid for 30 minutes. Only a SHA-256 hash of each link is stored, and asking for a new link cancels the old one."],
            ["Sessions", "Signing in sets a session cookie that page scripts cannot read, and it ends after 2 hours without use. Changes made while signed in need a second token, which blocks forged requests from other websites."],
            ["Your seats", "Seats are locked in the database while a booking is made, so the same seat can never be held or sold twice, even when thousands of people click at once."],
            ["Limits", "Checkout and sign-in requests are rate-limited, which slows down password guessing and ticket bots."],
        ],
    },
    payments: {
        title: "Payments",
        intro: "SeatLock's checkout is a demo today.",
        sections: [
            ["What happens now", "Pay confirms the booking straight away. No money is taken and no card details are asked for or stored."],
            ["With a real payment provider", "A card payment provider would be added at the Pay step. Card details would go directly to that provider and never be stored by SeatLock; your seats stay reserved while the payment is processed."],
            ["Refunds", "No payments are taken, so there is nothing to refund."],
        ],
    },
};

function isInfoRoute(hash) {
    return Object.hasOwn(INFO_PAGES, hash.slice(1));
}

function renderInfo(key) {
    const page = INFO_PAGES[key];
    const links = Object.entries(INFO_PAGES).map(([k, p]) =>
        el("a", { href: `#${k}`, class: k === key ? "active" : null, "aria-current": k === key ? "page" : null }, p.title));
    $("info-view").replaceChildren(el("div", { class: "info" },
        el("nav", { class: "info-nav", "aria-label": "Help and policies" }, links),
        el("article", { class: "card info-card" },
            el("h1", {}, page.title),
            el("p", { class: "muted" }, page.intro),
            page.sections.map(([heading, text]) => el("section", {}, el("h2", {}, heading), el("p", {}, text))),
            el("a", { class: "back-link", href: "#" }, "← Back to concerts"))));
    showPage("info");
}
