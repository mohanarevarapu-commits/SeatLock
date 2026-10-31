// The demo outbox page: lists the emails the demo kept instead of sending (GET /api/demo/outbox) and
// shows one at a time. Email HTML is shown in a sandboxed frame, so nothing in it can run.

const REFRESH_MS = 5000;
let emails = [];
let selectedId = null;

function node(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text != null) element.textContent = text;
    return element;
}

function when(iso) {
    return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "medium" });
}

async function load() {
    let response;
    try {
        response = await fetch("/api/demo/outbox", { headers: { Accept: "application/json" } });
    } catch {
        return showMessage("Could not reach SeatLock. Is the app running?");
    }
    if (!response.ok) {
        return showMessage("The demo outbox is off. It is only there when the app runs with the demo profile "
            + "and no mail server, and only on the computer the app runs on.");
    }
    emails = await response.json();
    renderList();
    if (selectedId == null && emails.length > 0) selectedId = emails[0].id;
    renderSelected();
}

function showMessage(text) {
    document.getElementById("outbox-list").replaceChildren();
    document.getElementById("outbox-view").replaceChildren(node("p", "muted", text));
}

function renderList() {
    const list = document.getElementById("outbox-list");
    if (emails.length === 0) {
        list.replaceChildren(node("li", "muted", "No emails yet. Book a concert, or ask for a ticket code."));
        return;
    }
    list.replaceChildren(...emails.map(email => {
        const button = node("button", "outbox-item" + (email.id === selectedId ? " active" : ""));
        button.type = "button";
        button.append(node("strong", null, email.subject), node("span", "muted small", email.to),
            node("span", "muted small", when(email.sentAt)));
        button.addEventListener("click", () => { selectedId = email.id; renderList(); renderSelected(); });
        return node("li", null).appendChild(button).parentNode;
    }));
}

function renderSelected() {
    const email = emails.find(e => e.id === selectedId);
    const view = document.getElementById("outbox-view");
    if (!email) return;
    const head = node("div", "outbox-meta");
    head.append(node("h2", null, email.subject), node("p", "muted small", `To ${email.to} · ${when(email.sentAt)}`));
    let body;
    if (email.html) {
        body = node("iframe", "outbox-frame");
        body.setAttribute("sandbox", "");          // no scripts, no access to this page
        body.setAttribute("title", email.subject);
        body.srcdoc = email.html;
    } else {
        body = node("pre", "outbox-text", email.text);
    }
    const current = view.querySelector("[data-email]");
    if (current && Number(current.dataset.email) === email.id) return;   // already showing it; keep the scroll
    const wrapper = node("div");
    wrapper.dataset.email = email.id;
    wrapper.append(head, body);
    view.replaceChildren(wrapper);
}

document.getElementById("refresh").addEventListener("click", load);
load();
setInterval(load, REFRESH_MS);
