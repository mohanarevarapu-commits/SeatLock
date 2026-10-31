// SeatLock web client: accounts. Sign up, log in, forgot password, the profile menu in the corner,
// and My bookings. Loaded after app.js and uses its helpers (el, api, toast, state, showPage, ...).
// Accounts are optional: guests can still book.

// Browsers compile pattern attributes with the "v" flag, where ( ) and - must be escaped inside [...];
// an invalid pattern is silently ignored, so keep every one of them escaped.
const PHONE_PATTERN = "\\+?[0-9 \\(\\)\\-]{7,20}";

// ---- who is signed in ----------------------------------------------------

async function loadMe() {
    try {
        const response = await fetch("/api/account", { headers: { Accept: "application/json" } });
        state.me = response.ok ? await response.json() : null;
    } catch {
        state.me = null;
    }
    renderAccountArea();
}

function initials(name) {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0].toUpperCase()).join("");
}

function firstName(name) {
    return name.split(/\s+/)[0];
}

// The corner of the header: Log in / Sign up, or the buyer's avatar with a menu.
function renderAccountArea() {
    const area = $("account-area");
    if (!state.me) {
        area.replaceChildren(
            el("a", { class: "btn secondary small", href: "#login" }, "Log in"),
            el("a", { class: "btn small", href: "#register" }, "Sign up"));
        return;
    }
    const me = state.me;
    const menu = el("div", { id: "account-menu", class: "menu", role: "menu", hidden: true },
        el("div", { class: "menu-head" },
            el("span", { class: "avatar large", "aria-hidden": "true" }, initials(me.name)),
            el("div", { class: "menu-who" },
                el("strong", {}, me.name),
                el("span", { class: "muted" }, me.email),
                me.phone ? el("span", { class: "muted" }, me.phone) : null)),
        el("a", { href: "#bookings", role: "menuitem" }, "My bookings"),
        el("a", { href: "#profile", role: "menuitem" }, "Profile"),
        el("button", { type: "button", role: "menuitem", onclick: logOut }, "Log out"));
    const toggle = el("button", {
        id: "account-toggle",
        class: "avatar-button",
        type: "button",
        "aria-haspopup": "menu",
        "aria-expanded": "false",
        onclick: () => setMenu(menu.hidden),
    }, el("span", { class: "avatar", "aria-hidden": "true" }, initials(me.name)),
        el("span", { class: "avatar-name" }, firstName(me.name)));
    area.replaceChildren(toggle, menu);
}

function setMenu(open) {
    const menu = $("account-menu");
    if (!menu) return;
    menu.hidden = !open;
    $("account-toggle").setAttribute("aria-expanded", String(open));
}

document.addEventListener("click", event => {
    if (!event.target.closest("#account-area")) setMenu(false);
});
document.addEventListener("keydown", event => {
    if (event.key === "Escape") setMenu(false);
});

async function logOut() {
    try {
        await api("/api/auth/logout", { method: "POST" });
    } catch (error) {
        toast(explain(error), true);
        return;
    }
    state.me = null;
    renderAccountArea();
    toast("You're logged out.");
    if (!$("account-view").hidden) location.hash = "";
    renderCart();
}

// ---- forms ---------------------------------------------------------------

function field(label, input, hint) {
    return el("label", { class: "field" },
        el("span", { class: "field-label" }, label),
        input,
        hint ? el("span", { class: "muted small" }, hint) : null);
}

function formValues(form) {
    return Object.fromEntries(new FormData(form));
}

// Runs a form's request with the button disabled; shows any error inside the form.
async function submitForm(form, errorBox, action) {
    const button = form.querySelector("button[type=submit]");
    button.disabled = true;
    errorBox.textContent = "";
    try {
        await action();
    } catch (error) {
        errorBox.textContent = explain(error);
    } finally {
        button.disabled = false;
    }
}

function showAuth(title, subtitle, body, footer = []) {
    $("auth-view").replaceChildren(el("div", { class: "auth" },
        el("div", { class: "card auth-card" },
            el("h1", {}, title),
            subtitle ? el("p", { class: "muted" }, subtitle) : null,
            body,
            footer.length ? el("div", { class: "auth-footer" }, footer) : null)));
    showPage("auth");
    $("auth-view").querySelector("input")?.focus();
}

// After logging in or signing up, go back to where the buyer was.
// ---- book as guest or log in ---------------------------------------------

// Book now (and Join the queue) first ask how to book: Log in, Sign up, or Continue as guest.
// A guest gives their email and phone number on the payment page, just before Pay now.
// Signed-in buyers go straight on.
function askHowToBook(action) {
    const run = action === "queue" ? queueSeats : bookNow;
    if (state.me) return run();
    document.getElementById("book-dialog")?.remove();

    const goToAuth = target => {
        dialog.close();
        state.resumeBooking = action;
        state.afterLogin = `#event-${state.event.id}`;
        location.hash = target;
    };
    const asGuest = () => {
        dialog.close();
        run();
    };
    const dialog = el("dialog", { id: "book-dialog", class: "book-dialog", "aria-labelledby": "book-dialog-title" },
        el("div", { class: "dialog-head" },
            el("h2", { id: "book-dialog-title" }, "How would you like to book?"),
            el("button", { class: "dialog-close", type: "button", "aria-label": "Close", onclick: () => dialog.close() }, "×")),
        el("div", { class: "book-option-actions" },
            el("button", { class: "btn", type: "button", onclick: () => goToAuth("#login") }, "Log in"),
            el("button", { class: "btn secondary", type: "button", onclick: () => goToAuth("#register") }, "Sign up")),
        el("p", { class: "muted small" }, "Keep every ticket in My bookings. Your seats stay selected while you log in."),
        el("div", { class: "or" }, el("span", {}, "or")),
        el("button", { class: "btn secondary guest-button", type: "button", onclick: asGuest }, "Continue as guest"),
        el("p", { class: "muted small" }, "No account needed: just your email and phone number on the next page."));
    dialog.addEventListener("close", () => dialog.remove());
    document.body.append(dialog);
    dialog.showModal();
    dialog.querySelector(".guest-button").focus();
}

function leaveAuth() {
    const target = state.afterLogin || (state.event ? `#event-${state.event.id}` : "");
    state.afterLogin = null;
    location.hash = target;
    renderCart();
}

function renderLogin() {
    const error = el("p", { class: "form-error", role: "alert" });
    const form = el("form", { class: "form" },
        field("Email", el("input", { name: "email", type: "email", autocomplete: "email", required: true })),
        field("Password", el("input", {
            name: "password", type: "password", autocomplete: "current-password", required: true,
        })),
        error,
        el("button", { class: "btn", type: "submit" }, "Log in"));
    form.addEventListener("submit", event => {
        event.preventDefault();
        const { email, password } = formValues(form);
        submitForm(form, error, async () => {
            state.me = await api("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
            renderAccountArea();
            toast(`Welcome back, ${firstName(state.me.name)}.`);
            leaveAuth();
        });
    });
    showAuth("Log in", "See your bookings and check out faster.", form, [
        el("a", { href: "#forgot" }, "Forgot password?"),
        el("span", {}, "New to SeatLock? ", el("a", { href: "#register" }, "Create an account")),
    ]);
}

function renderRegister() {
    const error = el("p", { class: "form-error", role: "alert" });
    const form = el("form", { class: "form" },
        field("Full name", el("input", { name: "name", autocomplete: "name", required: true, maxlength: 100 })),
        field("Email", el("input", { name: "email", type: "email", autocomplete: "email", required: true })),
        field("Phone (optional)", el("input", {
            name: "phone", type: "tel", autocomplete: "tel", pattern: PHONE_PATTERN,
            title: "Digits, spaces, brackets and dashes, e.g. +44 20 7946 0000",
        })),
        field("Password", el("input", {
            name: "password", type: "password", autocomplete: "new-password", required: true,
            minlength: 8, maxlength: 72,
        }), "At least 8 characters."),
        error,
        el("button", { class: "btn", type: "submit" }, "Create account"));
    form.addEventListener("submit", event => {
        event.preventDefault();
        const values = formValues(form);
        submitForm(form, error, async () => {
            state.me = await api("/api/auth/register", { method: "POST", body: JSON.stringify(values) });
            renderAccountArea();
            toast(`Account created. Welcome, ${firstName(state.me.name)}!`);
            leaveAuth();
        });
    });
    showAuth("Create your account", "Guests can still book; an account keeps all your bookings together.", form, [
        el("span", {}, "Already have an account? ", el("a", { href: "#login" }, "Log in")),
    ]);
}

function renderForgot() {
    const error = el("p", { class: "form-error", role: "alert" });
    const form = el("form", { class: "form" },
        field("Email", el("input", { name: "email", type: "email", autocomplete: "email", required: true })),
        error,
        el("button", { class: "btn", type: "submit" }, "Email me a reset link"));
    form.addEventListener("submit", event => {
        event.preventDefault();
        const { email } = formValues(form);
        submitForm(form, error, async () => {
            await api("/api/auth/forgot-password", { method: "POST", body: JSON.stringify({ email }) });
            showAuth("Check your email", null, el("p", {},
                `If an account exists for ${email}, we've sent it a link to choose a new password. ` +
                "The link works for 30 minutes."), [el("a", { href: "#login" }, "Back to log in")]);
        });
    });
    showAuth("Forgot your password?", "Enter your email and we'll send you a link to reset it.", form, [
        el("a", { href: "#login" }, "Back to log in"),
    ]);
}

function renderReset(token) {
    const error = el("p", { class: "form-error", role: "alert" });
    const form = el("form", { class: "form" },
        field("New password", el("input", {
            name: "password", type: "password", autocomplete: "new-password", required: true,
            minlength: 8, maxlength: 72,
        }), "At least 8 characters."),
        field("Repeat new password", el("input", {
            name: "repeat", type: "password", autocomplete: "new-password", required: true,
        })),
        error,
        el("button", { class: "btn", type: "submit" }, "Set new password"));
    form.addEventListener("submit", event => {
        event.preventDefault();
        const { password, repeat } = formValues(form);
        if (password !== repeat) {
            error.textContent = "The two passwords are not the same.";
            return;
        }
        submitForm(form, error, async () => {
            await api("/api/auth/reset-password", { method: "POST", body: JSON.stringify({ token, password }) });
            toast("Password changed. Log in with your new password.");
            location.hash = "#login";
        });
    });
    showAuth("Choose a new password", null, form, [el("a", { href: "#forgot" }, "Need a new link?")]);
}

// ---- My bookings and profile ---------------------------------------------

async function renderAccount(focus) {
    if (!state.me) {
        state.afterLogin = location.hash;
        location.hash = "#login";
        return;
    }
    showPage("account");
    const view = $("account-view");
    view.replaceChildren(el("p", { class: "muted" }, "Loading your bookings…"));
    let bookings;
    try {
        bookings = await api("/api/account/bookings");
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
            // The session ended (e.g. timed out): log in again.
            state.me = null;
            renderAccountArea();
            renderAccount(focus);
            return;
        }
        toast(explain(error), true);
        bookings = { upcoming: [], past: [] };
    }
    const me = state.me;
    view.replaceChildren(el("div", { class: "account-page" },
        el("div", { class: "account-header" },
            el("span", { class: "avatar large", "aria-hidden": "true" }, initials(me.name)),
            el("div", {},
                el("h1", {}, me.name),
                el("p", { class: "muted" }, [me.email, me.phone].filter(Boolean).join(" · ")))),
        el("div", { class: "account-grid" }, bookingsCard(bookings), profileCard(me))));
    if (focus === "profile") $("profile-name").focus();
}

function bookingsCard(bookings) {
    const card = el("section", { class: "card", "aria-label": "My bookings" });
    const tab = (key, label) => el("button", {
        class: "tab" + (state.bookingsTab === key ? " active" : ""),
        type: "button",
        role: "tab",
        "aria-selected": String(state.bookingsTab === key),
        onclick: () => { state.bookingsTab = key; draw(); },
    }, label);
    const draw = () => {
        const list = bookings[state.bookingsTab];
        card.replaceChildren(
            el("h2", {}, "My bookings"),
            el("div", { class: "tabs", role: "tablist" },
                tab("upcoming", `Active (${bookings.upcoming.length})`),
                tab("past", `Past (${bookings.past.length})`)),
            list.length
                ? el("ul", { class: "booking-list" }, list.map(bookingItem))
                : el("p", { class: "muted empty" },
                    state.bookingsTab === "upcoming" ? "No bookings for upcoming shows yet. " : "No past shows yet. ",
                    el("a", { href: "#" }, "Find a show")));
    };
    state.bookingsTab = state.bookingsTab || "upcoming";
    draw();
    return card;
}

function bookingItem(booking) {
    return el("li", { class: "booking-item" },
        el("div", { class: "booking-main" },
            el("a", { class: "booking-title", href: `#event-${booking.eventId}` }, booking.eventName),
            el("span", { class: "muted" },
                `${booking.venueName}, ${booking.venueCity} · ${dateTime(booking.startsAt)}`),
            el("span", {}, booking.seats.map(s => `${s.section} ${s.label}`).join(", "))),
        el("div", { class: "booking-side" },
            el("span", { class: "ref" }, booking.reference),
            el("strong", {}, money(booking.totalCents))));
}

function profileCard(me) {
    const error = el("p", { class: "form-error", role: "alert" });
    const form = el("form", { class: "form" },
        field("Full name", el("input", {
            id: "profile-name", name: "name", value: me.name, required: true, maxlength: 100, autocomplete: "name",
        })),
        field("Email", el("input", { value: me.email, disabled: true }), "Your sign-in email can't be changed."),
        field("Phone", el("input", {
            name: "phone", type: "tel", value: me.phone || "", pattern: PHONE_PATTERN, autocomplete: "tel",
        })),
        error,
        el("button", { class: "btn", type: "submit" }, "Save changes"));
    form.addEventListener("submit", event => {
        event.preventDefault();
        const { name, phone } = formValues(form);
        submitForm(form, error, async () => {
            state.me = await api("/api/account", { method: "PUT", body: JSON.stringify({ name, phone }) });
            renderAccountArea();
            toast("Profile saved.");
            renderAccount();
        });
    });
    return el("section", { class: "card", id: "profile", "aria-label": "Profile" }, el("h2", {}, "Profile"), form);
}

// ---- routing -------------------------------------------------------------

// Leaving the payment page for a log-in or account page gives the seats back, like Cancel.
async function releaseHoldBeforeLeaving() {
    const hold = state.hold;
    setHold(null);
    try {
        await api(`/api/holds/${hold.id}`, { method: "DELETE" });
        toast("Booking cancelled. Your seats are back on sale.");
    } catch {
        // The hold lapses on its own anyway.
    }
}

function route() {
    setMenu(false);
    const hash = location.hash;
    if (hash === "#checkout") return;
    const accountRoute = hash === "#login" || hash === "#register" || hash === "#forgot"
        || hash.startsWith("#reset/") || hash === "#bookings" || hash === "#profile";
    if (isInfoRoute(hash)) {
        // Help and policy pages; leaving the payment page is handled by its popstate listener.
        renderInfo(hash.slice(1));
        return;
    }
    if (!accountRoute) {
        // A show (#event-N) or the home page. Leaving the payment page is handled by its popstate listener.
        const event = state.events.find(e => `#event-${e.id}` === hash);
        if (event) openEvent(event);
        else showHome(homeListOf(hash));
        // Back from logging in after Book now: carry on with the seats still selected.
        const action = state.resumeBooking;
        state.resumeBooking = null;
        if (action && event && state.me && state.selected.size > 0) askHowToBook(action);
        return;
    }
    if (state.hold && !$("checkout-view").hidden) releaseHoldBeforeLeaving();
    if ((hash === "#login" || hash === "#register") && state.me) {
        location.hash = "#bookings";
    } else if (hash === "#login") {
        renderLogin();
    } else if (hash === "#register") {
        renderRegister();
    } else if (hash === "#forgot") {
        renderForgot();
    } else if (hash.startsWith("#reset/")) {
        renderReset(hash.slice("#reset/".length));
    } else {
        renderAccount(hash.slice(1));
    }
}

window.addEventListener("hashchange", route);

// ---- start-up ------------------------------------------------------------

(async () => {
    await Promise.all([loadMe(), loadEvents()]);
    // Opening SeatLock always starts on the page in the address (the home page for a plain link).
    // Only a reload of the payment page goes back to the seats still being paid for.
    const savedHoldId = storage("get", "seatlock.holdId");
    if (location.hash === "#checkout" && savedHoldId && await restoreHold(savedHoldId)) return;
    if (location.hash === "#checkout") history.replaceState(null, "", location.pathname);
    // Seats from a payment that was left unfinished go back on sale instead of waiting out the timer.
    if (savedHoldId && !state.hold) {
        storage("remove", "seatlock.holdId");
        api(`/api/holds/${savedHoldId}`, { method: "DELETE" }).catch(() => { /* it lapses on its own anyway */ });
    }
    route();
})();
