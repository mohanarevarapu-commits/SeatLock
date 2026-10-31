// SeatLock web client: the help and policy pages linked from the footer (#help, #terms, #privacy,
// #security, #payments). Loaded after app.js and uses its helpers (el, $, showPage).
// Every statement here describes what SeatLock actually does; keep it true when behaviour changes.

const INFO_PAGES = {
    help: {
        title: "Help & support",
        intro: "Answers to the questions buyers ask most.",
        sections: [
            ["How do I book?", "Open a concert, click the seats you want on the map (up to 8), then Book now. Choose Log in, Sign up or Continue as guest. As a guest, enter your email and phone number on the payment page, then Pay now. You get a booking reference straight away."],
            ["How do I pay?", "The payment page is a demo. Pay by card with the test card 4242 4242 4242 4242 (any future expiry, any CVC), or by UPI with success@demo. Use a test card fills the card in for you. The card 4000 0000 0000 0002 and the UPI ID failure@demo are always declined, so you can try a failed payment. Real cards are refused."],
            ["How long are my seats kept?", "10 minutes from Book now. The timer on the payment page shows how long is left. While it runs, nobody else can take those seats."],
            ["What if the timer runs out?", "The seats go back on sale and you return to the seat map. Pick seats again to book."],
            ["Where are my tickets?", "Your ticket, with its QR code, is shown when you pay and emailed to you. Show the QR code or the booking reference at the entrance. To see your tickets again, use Find my tickets (guests) or My bookings (signed in); Find a booking on any concert page looks one up by its reference."],
            ["How do I check my tickets as a guest?", "Open Find my tickets at the top of any page. Enter the email and phone number you gave when you paid and click Send code; we email you a 6-digit code. Enter it to see all your tickets with their QR codes. The code works once, for 10 minutes. If no email arrives, check the details are the ones you booked with."],
            ["I forgot my password", "Use Forgot password on the log-in page. We email a link that works once, for 30 minutes."],
            ["Can I cancel a paid booking?", "Not online yet. You can cancel before paying with Cancel and go back, which puts the seats back on sale at once."],
            ["Do I need an account?", "No. Anyone can browse. When you click Book now you can log in or sign up, or continue as a guest and give your email and phone number when you pay. An account keeps your bookings together in My bookings."],
            ["Where are my booking details sent?", "After you pay, to your account's email, or to the email you gave as a guest. The email is your ticket: the QR code, booking reference, concert and time, the venue's address, every seat by section, row and number, and the total."],
        ],
    },
    terms: {
        title: "Terms of use",
        intro: "The short version of how SeatLock works. SeatLock is a demo, so bookings are not real tickets.",
        sections: [
            ["Bookings", "A booking is made when you pay before the 10-minute timer ends. Up to 8 seats can be booked at a time. Each booking gets a reference."],
            ["Payment", "The checkout is a demo: it accepts only test card numbers and test UPI IDs, takes no money, and a booking made with them is not a real ticket."],
            ["Fair use", "To keep busy on-sales fair, checkout and sign-in requests are limited per IP address, and the queued checkout may be used at peak times. Automated bulk buying is not allowed."],
            ["Accounts", "You are responsible for keeping your password private. Bookings made while signed in can only be seen and paid for from your account."],
            ["Changes", "These terms may change as SeatLock grows; the version on this page applies."],
        ],
    },
    privacy: {
        title: "Your data & privacy",
        intro: "We keep as little about you as we can.",
        sections: [
            ["Booking as a guest", "We ask for your email and phone number when you pay and keep them with your booking; if you leave without paying, nothing is kept. The email is used to send your booking details; the phone number only in case we need to reach you about that booking. We do not send text messages. Your browser also keeps a random reference so it can find your seats again."],
            ["With an account", "We store your name, email address and, if you give it, your phone number, plus the bookings you make while signed in. You can change your name and phone in Profile."],
            ["Ticket codes", "When you use Find my tickets we email you a one-time code. We keep only a scrambled (hashed) copy of it, for 10 minutes, so we can check it; never the code itself."],
            ["What we never store", "Card, UPI or bank details: what you type on the demo payment page is checked in your browser and never sent to SeatLock. Your password itself: only a one-way hash of it is kept."],
            ["Emails", "We email you only about your bookings and your account: booking confirmations (your tickets), Find my tickets codes, a welcome message, password reset links and a notice when your password changes. Email contents are never written to our logs."],
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
            ["Find my tickets codes", "Six digits, sent only to the email you booked with. A code works once, for 10 minutes, and five wrong tries end it; we store only a BCrypt hash of it. Asking for a code gives the same answer whether or not an email has bookings, so nobody can use it to find out who has booked, and requests are rate-limited."],
            ["Your seats", "Seats are locked in the database while a booking is made, so the same seat can never be held or sold twice, even when thousands of people click at once."],
            ["Limits", "Checkout and sign-in requests are rate-limited, which slows down password guessing and ticket bots."],
        ],
    },
    payments: {
        title: "Payments",
        intro: "SeatLock's checkout is a demo today.",
        sections: [
            ["What happens now", "The payment page has a card form and a UPI option, but no money is taken. Only test details work: card 4242 4242 4242 4242 or 5555 5555 5555 4444 (approved), 4000 0000 0000 0002 (declined), 4000 0000 0000 9995 (insufficient funds), and UPI success@demo (approved) or failure@demo (declined). Any other card or UPI ID is refused."],
            ["Your details", "Card and UPI details are checked in your browser and never sent to SeatLock or stored. After an approved test payment, SeatLock books your seats and the form is cleared."],
            ["Your seats while you pay", "They stay reserved for you until the 10-minute timer ends, including while a payment is being processed. A declined payment keeps them reserved so you can try again."],
            ["With a real payment provider", "A card or UPI payment provider would replace the demo step. Card details would go directly to that provider and never be stored by SeatLock, and the booking would only be confirmed once the provider reports the payment."],
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
