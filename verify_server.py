"""
Naven-Modern Verification Server

Fluent Design GUI + REST API for managing user verification.

Usage:
    python verify_server.py [--port PORT] [--no-gui]

API Endpoints:
    POST   /verify          {username, password} -> token
    GET    /api/users       -> [{username, token, created, expires, active}]
    POST   /api/users       {username, password, days} -> {token, expires}
    PUT    /api/users       {username, password?, days?} -> {token, expires}
    DELETE /api/users       {username} -> {ok}
    GET    /health          -> {status: ok}
"""

import argparse
import hashlib
import json
import os
import secrets
import sys
import threading
import time
import uuid
from datetime import datetime, timedelta
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path

DATA_FILE = Path(__file__).parent / "users.json"
_users_lock = threading.Lock()


# ─── User Store ───────────────────────────────────────────────────────────────

def _load_users():
    if DATA_FILE.exists():
        with open(DATA_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def _save_users(users):
    with open(DATA_FILE, "w", encoding="utf-8") as f:
        json.dump(users, f, indent=2, ensure_ascii=False)


def _hash_pw(pw):
    return hashlib.sha256(pw.encode("utf-8")).hexdigest()


def get_user(username):
    users = _load_users()
    return users.get(username)


def add_user(username, password, days=30):
    with _users_lock:
        users = _load_users()
        if username in users:
            return None, "User already exists"
        now = datetime.utcnow()
        token = str(uuid.uuid4())
        users[username] = {
            "password": _hash_pw(password),
            "token": token,
            "created": now.isoformat(),
            "expires": (now + timedelta(days=days)).isoformat(),
            "active": True,
        }
        _save_users(users)
        return users[username], None


def update_user(username, password=None, days=None):
    with _users_lock:
        users = _load_users()
        if username not in users:
            return None, "User not found"
        user = users[username]
        if password is not None:
            user["password"] = _hash_pw(password)
        if days is not None:
            base = datetime.utcnow()
            user["expires"] = (base + timedelta(days=days)).isoformat()
        user["active"] = True
        users[username] = user
        _save_users(users)
        return user, None


def delete_user(username):
    with _users_lock:
        users = _load_users()
        if username not in users:
            return False
        del users[username]
        _save_users(users)
        return True


def list_users():
    users = _load_users()
    result = []
    for uname, u in users.items():
        result.append({
            "username": uname,
            "token": u.get("token", ""),
            "created": u.get("created", ""),
            "expires": u.get("expires", ""),
            "active": u.get("active", False),
        })
    return result


def verify_user(username, password):
    user = get_user(username)
    if not user:
        return False, "User not found"
    if not user.get("active", False):
        return False, "Account disabled"
    expires = user.get("expires")
    if expires:
        try:
            exp_dt = datetime.fromisoformat(expires)
            if datetime.utcnow() > exp_dt:
                return False, "Account expired"
        except Exception:
            pass
    if user.get("password") != _hash_pw(password):
        return False, "Invalid password"
    return True, user.get("token", "")


# ─── HTTP Server ──────────────────────────────────────────────────────────────

class VerifyHTTPHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        ts = datetime.now().strftime("%H:%M:%S")
        print(f"[{ts}] {self.client_address[0]} - {fmt % args}")

    def _send_json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self):
        length = int(self.headers.get("Content-Length", 0))
        if length == 0:
            return {}
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        if self.path == "/health":
            self._send_json(200, {"status": "ok"})
        elif self.path == "/api/users":
            self._send_json(200, list_users())
        else:
            self._send_json(404, {"error": "Not found"})

    def do_POST(self):
        data = self._read_json()
        if self.path == "/verify":
            ok, result = verify_user(data.get("username", ""), data.get("password", ""))
            if ok:
                self.send_response(200)
                self.send_header("Content-Type", "text/plain")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(result.encode())
            else:
                self._send_json(401, {"error": result})
        elif self.path == "/api/users":
            username = data.get("username", "").strip()
            password = data.get("password", "").strip()
            days = int(data.get("days", 30))
            if not username or not password:
                self._send_json(400, {"error": "username and password required"})
                return
            user, err = add_user(username, password, days)
            if err:
                self._send_json(409, {"error": err})
            else:
                self._send_json(201, {
                    "username": username,
                    "token": user["token"],
                    "expires": user["expires"],
                })
        else:
            self._send_json(404, {"error": "Not found"})

    def do_PUT(self):
        data = self._read_json()
        if self.path == "/api/users":
            username = data.get("username", "").strip()
            if not username:
                self._send_json(400, {"error": "username required"})
                return
            password = data.get("password")
            days = int(data["days"]) if "days" in data else None
            user, err = update_user(username, password, days)
            if err:
                self._send_json(404, {"error": err})
            else:
                self._send_json(200, {
                    "username": username,
                    "token": user["token"],
                    "expires": user["expires"],
                })
        else:
            self._send_json(404, {"error": "Not found"})

    def do_DELETE(self):
        data = self._read_json()
        if self.path == "/api/users":
            username = data.get("username", "").strip()
            if not username:
                self._send_json(400, {"error": "username required"})
                return
            ok = delete_user(username)
            if ok:
                self._send_json(200, {"ok": True})
            else:
                self._send_json(404, {"error": "User not found"})
        else:
            self._send_json(404, {"error": "Not found"})


def start_server(port):
    server = HTTPServer(("0.0.0.0", port), VerifyHTTPHandler)
    print(f"API server listening on http://0.0.0.0:{port}")
    server.serve_forever()


# ─── Fluent Design GUI ────────────────────────────────────────────────────────

class FluentColors:
    BG = "#0d1117"
    SIDEBAR = "#161b22"
    CARD = "#161b22"
    BORDER = "#30363d"
    ACCENT = "#58a6ff"
    ACCENT_HOVER = "#79b8ff"
    TEXT = "#e6edf3"
    TEXT_DIM = "#8b949e"
    INPUT_BG = "#0d1117"
    SUCCESS = "#3fb950"
    ERROR = "#f85149"
    ROW_ALT = "#1c2128"
    BTN_PRIMARY = "#238636"
    BTN_DANGER = "#da3633"


def _font(size=11, bold=False):
    import tkinter.font as tkfont
    weight = "bold" if bold else "normal"
    families = ["Segoe UI", "Segoe UI Semibold", "Microsoft YaHei UI", "Arial"]
    for f in families:
        try:
            return tkfont.Font(family=f, size=size, weight=weight)
        except Exception:
            continue
    return tkfont.Font(size=size, weight=weight)


import tkinter as tk
from tkinter import ttk, messagebox


class FluentEntry(tk.Frame):
    def __init__(self, parent, placeholder="", show=None, **kw):
        super().__init__(parent, bg=FluentColors.INPUT_BG, highlightbackground=FluentColors.BORDER,
                         highlightthickness=1, **kw)
        self._ph = placeholder
        self._show = show
        self._focused = False

        self.entry = tk.Entry(self, bg=FluentColors.INPUT_BG, fg=FluentColors.TEXT,
                              insertbackground=FluentColors.TEXT, font=_font(11),
                              relief="flat", bd=0, highlightthickness=0,
                              show=show)
        self.entry.pack(fill="x", padx=8, pady=6)

        self.entry.bind("<FocusIn>", self._on_focus)
        self.entry.bind("<FocusOut>", self._on_blur)
        self._show_placeholder()

    def _show_placeholder(self):
        if not self.entry.get():
            self.entry.config(fg=FluentColors.TEXT_DIM)
            self.entry.insert(0, self._ph)
            self.entry.bind("<FocusIn>", self._on_focus_ph)

    def _on_focus_ph(self, event=None):
        self.entry.unbind("<FocusIn>")
        self.entry.delete(0, "end")
        self.entry.config(fg=FluentColors.TEXT)
        self._on_focus()

    def _on_focus(self, event=None):
        self._focused = True
        self.config(highlightbackground=FluentColors.ACCENT)

    def _on_blur(self, event=None):
        self._focused = False
        self.config(highlightbackground=FluentColors.BORDER)
        self._show_placeholder()

    def get(self):
        val = self.entry.get()
        if val == self._ph:
            return ""
        return val

    def set(self, val):
        self.entry.config(fg=FluentColors.TEXT)
        self.entry.delete(0, "end")
        self.entry.insert(0, val)


class FluentButton(tk.Frame):
    def __init__(self, parent, text, color=None, hover_color=None, command=None, width=120, height=34, **kw):
        super().__init__(parent, bg=color or FluentColors.BTN_PRIMARY, cursor="hand2", **kw)
        self.configure(width=width, height=height)
        self.pack_propagate(False)
        self._color = color or FluentColors.BTN_PRIMARY
        self._hover = hover_color or FluentColors.SUCCESS
        self._cmd = command

        self._label = tk.Label(self, text=text, bg=self._color, fg=FluentColors.TEXT,
                               font=_font(11, True))
        self._label.pack(expand=True, fill="both")

        for w in (self, self._label):
            w.bind("<Enter>", self._on_enter)
            w.bind("<Leave>", self._on_leave)
            w.bind("<Button-1>", self._on_click)

    def _on_enter(self, e=None):
        self.configure(bg=self._hover)
        self._label.configure(bg=self._hover)

    def _on_leave(self, e=None):
        self.configure(bg=self._color)
        self._label.configure(bg=self._color)

    def _on_click(self, e=None):
        if self._cmd:
            self._cmd()


class ServerGUI:
    def __init__(self, port):
        self.port = port
        self.root = tk.Tk()
        self.root.title(f"Naven Verification Server - Port {port}")
        self.root.geometry("900x600")
        self.root.configure(bg=FluentColors.BG)
        self.root.minsize(750, 500)

        self._build_ui()
        self._refresh_users()

    def _build_ui(self):
        root = self.root

        sidebar = tk.Frame(root, bg=FluentColors.SIDEBAR, width=200)
        sidebar.pack(side="left", fill="y")
        sidebar.pack_propagate(False)

        logo = tk.Label(sidebar, text="Naven", bg=FluentColors.SIDEBAR, fg=FluentColors.ACCENT,
                        font=_font(18, True))
        logo.pack(pady=(24, 4), padx=16, anchor="w")

        tk.Label(sidebar, text="Verification Server", bg=FluentColors.SIDEBAR,
                 fg=FluentColors.TEXT_DIM, font=_font(9)).pack(padx=16, anchor="w")

        sep = tk.Frame(sidebar, bg=FluentColors.BORDER, height=1)
        sep.pack(fill="x", padx=12, pady=16)

        btn_users = tk.Label(sidebar, text="  Users", bg=FluentColors.SIDEBAR, fg=FluentColors.TEXT,
                             font=_font(12), cursor="hand2", anchor="w")
        btn_users.pack(fill="x", padx=8, pady=2)
        btn_users.bind("<Button-1>", lambda e: self._show_panel("users"))
        btn_users.bind("<Enter>", lambda e: btn_users.config(bg=FluentColors.ROW_ALT))
        btn_users.bind("<Leave>", lambda e: btn_users.config(bg=FluentColors.SIDEBAR))

        btn_api = tk.Label(sidebar, text="  API Docs", bg=FluentColors.SIDEBAR, fg=FluentColors.TEXT,
                           font=_font(12), cursor="hand2", anchor="w")
        btn_api.pack(fill="x", padx=8, pady=2)
        btn_api.bind("<Button-1>", lambda e: self._show_panel("api"))
        btn_api.bind("<Enter>", lambda e: btn_api.config(bg=FluentColors.ROW_ALT))
        btn_api.bind("<Leave>", lambda e: btn_api.config(bg=FluentColors.SIDEBAR))

        sep2 = tk.Frame(sidebar, bg=FluentColors.BORDER, height=1)
        sep2.pack(fill="x", padx=12, pady=16)

        status_color = FluentColors.SUCCESS
        status_text = f"Running on :{self.port}"
        tk.Label(sidebar, text=status_text, bg=FluentColors.SIDEBAR, fg=status_color,
                 font=_font(9)).pack(padx=16, anchor="w", side="bottom", pady=12)

        self.content = tk.Frame(root, bg=FluentColors.BG)
        self.content.pack(side="left", fill="both", expand=True)

        self.panels = {}
        self._build_users_panel()
        self._build_api_panel()
        self._show_panel("users")

    def _build_users_panel(self):
        panel = tk.Frame(self.content, bg=FluentColors.BG)
        self.panels["users"] = panel

        header = tk.Frame(panel, bg=FluentColors.BG)
        header.pack(fill="x", padx=20, pady=(16, 8))

        tk.Label(header, text="User Management", bg=FluentColors.BG, fg=FluentColors.TEXT,
                 font=_font(16, True)).pack(side="left")

        add_btn = FluentButton(header, "+ Add User", FluentColors.BTN_PRIMARY, "#2ea043",
                               self._show_add_dialog, width=110, height=32)
        add_btn.pack(side="right")

        search_frame = tk.Frame(panel, bg=FluentColors.BG)
        search_frame.pack(fill="x", padx=20, pady=(0, 8))
        self.search_entry = FluentEntry(search_frame, placeholder="Search users...", width=300)
        self.search_entry.pack(side="left")
        self.search_entry.entry.bind("<KeyRelease>", lambda e: self._filter_users())

        table_frame = tk.Frame(panel, bg=FluentColors.CARD, highlightbackground=FluentColors.BORDER,
                               highlightthickness=1)
        table_frame.pack(fill="both", expand=True, padx=20, pady=(0, 16))

        cols = ("username", "token", "created", "expires", "active", "actions")
        self.tree = ttk.Treeview(table_frame, columns=cols, show="headings", height=15)

        style = ttk.Style()
        style.theme_use("clam")
        style.configure("Treeview", background=FluentColors.CARD, foreground=FluentColors.TEXT,
                        fieldbackground=FluentColors.CARD, borderwidth=0, font=_font(10))
        style.configure("Treeview.Heading", background=FluentColors.SIDEBAR, foreground=FluentColors.TEXT,
                        font=_font(10, True), borderwidth=0)
        style.map("Treeview", background=[("selected", FluentColors.ACCENT)],
                  foreground=[("selected", "#ffffff")])
        style.configure("Treeview.Row", height=28)

        self.tree.heading("username", text="Username")
        self.tree.heading("token", text="Token")
        self.tree.heading("created", text="Created")
        self.tree.heading("expires", text="Expires")
        self.tree.heading("active", text="Active")
        self.tree.heading("actions", text="")

        self.tree.column("username", width=120, minwidth=80)
        self.tree.column("token", width=200, minwidth=120)
        self.tree.column("created", width=100, minwidth=80)
        self.tree.column("expires", width=100, minwidth=80)
        self.tree.column("active", width=60, minwidth=50)
        self.tree.column("actions", width=140, minwidth=100)

        scrollbar = ttk.Scrollbar(table_frame, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y")
        self.tree.pack(fill="both", expand=True)

        btn_frame = tk.Frame(table_frame, bg=FluentColors.CARD)
        btn_frame.pack(fill="x", padx=8, pady=6)

        FluentButton(btn_frame, "Edit", FluentColors.ACCENT, FluentColors.ACCENT_HOVER,
                     self._edit_selected, width=80, height=28).pack(side="left", padx=4)
        FluentButton(btn_frame, "Delete", FluentColors.ERROR, "#e5534b",
                     self._delete_selected, width=80, height=28).pack(side="left", padx=4)
        FluentButton(btn_frame, "Refresh", FluentColors.SIDEBAR, FluentColors.ROW_ALT,
                     self._refresh_users, width=80, height=28).pack(side="left", padx=4)

    def _build_api_panel(self):
        panel = tk.Frame(self.content, bg=FluentColors.BG)
        self.panels["api"] = panel

        tk.Label(panel, text="API Documentation", bg=FluentColors.BG, fg=FluentColors.TEXT,
                 font=_font(16, True)).pack(anchor="w", padx=20, pady=(16, 12))

        docs = [
            ("POST /verify", "Authenticate user", '{"username":"...", "password":"..."}', "token string"),
            ("GET /api/users", "List all users", "—", "[{username, token, created, expires, active}]"),
            ("POST /api/users", "Create user", '{"username":"...", "password":"...", "days":30}', "{token, expires}"),
            ("PUT /api/users", "Update user", '{"username":"...", "password":"...", "days":60}', "{token, expires}"),
            ("DELETE /api/users", "Delete user", '{"username":"..."}', "{ok: true}"),
            ("GET /health", "Health check", "—", "{status: ok}"),
        ]

        for path, desc, req, resp in docs:
            card = tk.Frame(panel, bg=FluentColors.CARD, highlightbackground=FluentColors.BORDER,
                            highlightthickness=1)
            card.pack(fill="x", padx=20, pady=4)

            top = tk.Frame(card, bg=FluentColors.CARD)
            top.pack(fill="x", padx=12, pady=(8, 2))

            method_color = FluentColors.SUCCESS if "GET" in path else FluentColors.ACCENT
            if "DELETE" in path:
                method_color = FluentColors.ERROR
            elif "PUT" in path:
                method_color = "#d29922"

            tk.Label(top, text=path.split()[0], bg=FluentColors.CARD, fg=method_color,
                     font=_font(10, True), width=7, anchor="w").pack(side="left")
            tk.Label(top, text=path.split()[1], bg=FluentColors.CARD, fg=FluentColors.TEXT,
                     font=_font(11, True)).pack(side="left", padx=(0, 12))
            tk.Label(top, text=desc, bg=FluentColors.CARD, fg=FluentColors.TEXT_DIM,
                     font=_font(10)).pack(side="left")

            detail = tk.Frame(card, bg=FluentColors.CARD)
            detail.pack(fill="x", padx=12, pady=(0, 8))

            tk.Label(detail, text=f"Body: {req}", bg=FluentColors.CARD, fg=FluentColors.TEXT_DIM,
                     font=_font(9), anchor="w", wraplength=600, justify="left").pack(anchor="w")
            tk.Label(detail, text=f"Resp: {resp}", bg=FluentColors.CARD, fg=FluentColors.TEXT_DIM,
                     font=_font(9), anchor="w", wraplength=600, justify="left").pack(anchor="w")

    def _show_panel(self, name):
        for p in self.panels.values():
            p.pack_forget()
        self.panels[name].pack(fill="both", expand=True)

    def _refresh_users(self):
        for item in self.tree.get_children():
            self.tree.delete(item)
        for u in list_users():
            exp = u["expires"][:10] if u["expires"] else "—"
            created = u["created"][:10] if u["created"] else "—"
            token_short = u["token"][:12] + "..." if len(u["token"]) > 12 else u["token"]
            active = "Yes" if u["active"] else "No"
            self.tree.insert("", "end", values=(u["username"], token_short, created, exp, active, ""))

    def _filter_users(self):
        query = self.search_entry.get().lower()
        for item in self.tree.get_children():
            self.tree.delete(item)
        for u in list_users():
            if query and query not in u["username"].lower():
                continue
            exp = u["expires"][:10] if u["expires"] else "—"
            created = u["created"][:10] if u["created"] else "—"
            token_short = u["token"][:12] + "..." if len(u["token"]) > 12 else u["token"]
            active = "Yes" if u["active"] else "No"
            self.tree.insert("", "end", values=(u["username"], token_short, created, exp, active, ""))

    def _show_add_dialog(self):
        self._open_user_dialog("Add User")

    def _edit_selected(self):
        sel = self.tree.selection()
        if not sel:
            messagebox.showinfo("Info", "Select a user first")
            return
        username = self.tree.item(sel[0])["values"][0]
        self._open_user_dialog("Edit User", username)

    def _open_user_dialog(self, title, existing_user=None):
        dlg = tk.Toplevel(self.root)
        dlg.title(title)
        dlg.geometry("380x320")
        dlg.configure(bg=FluentColors.BG)
        dlg.transient(self.root)
        dlg.grab_set()

        tk.Label(dlg, text=title, bg=FluentColors.BG, fg=FluentColors.TEXT,
                 font=_font(14, True)).pack(pady=(16, 12), padx=20, anchor="w")

        form = tk.Frame(dlg, bg=FluentColors.BG)
        form.pack(fill="x", padx=20)

        tk.Label(form, text="Username", bg=FluentColors.BG, fg=FluentColors.TEXT_DIM,
                 font=_font(10)).pack(anchor="w")
        ue = FluentEntry(form, placeholder="username")
        ue.pack(fill="x", pady=(2, 8))
        if existing_user:
            ue.set(existing_user)
            ue.entry.config(state="disabled")

        tk.Label(form, text="Password", bg=FluentColors.BG, fg=FluentColors.TEXT_DIM,
                 font=_font(10)).pack(anchor="w")
        pe = FluentEntry(form, placeholder="password", show="*")
        pe.pack(fill="x", pady=(2, 8))

        tk.Label(form, text="Duration (days)", bg=FluentColors.BG, fg=FluentColors.TEXT_DIM,
                 font=_font(10)).pack(anchor="w")
        de = FluentEntry(form, placeholder="30")
        de.pack(fill="x", pady=(2, 12))

        if existing_user:
            user = get_user(existing_user)
            if user and user.get("expires"):
                try:
                    exp = datetime.fromisoformat(user["expires"])
                    remaining = (exp - datetime.utcnow()).days
                    de.set(str(max(remaining, 1)))
                except Exception:
                    pass

        def submit():
            username = ue.get().strip()
            password = pe.get().strip()
            days_str = de.get().strip() or "30"
            try:
                days = int(days_str)
            except ValueError:
                messagebox.showerror("Error", "Days must be a number")
                return

            if existing_user:
                pw = password if password else None
                user, err = update_user(username, pw, days)
            else:
                if not username or not password:
                    messagebox.showerror("Error", "Username and password required")
                    return
                user, err = add_user(username, password, days)

            if err:
                messagebox.showerror("Error", err)
            else:
                self._refresh_users()
                dlg.destroy()

        btn_frame = tk.Frame(dlg, bg=FluentColors.BG)
        btn_frame.pack(fill="x", padx=20, pady=16)
        FluentButton(btn_frame, "Save", FluentColors.BTN_PRIMARY, "#2ea043", submit,
                     width=100, height=32).pack(side="left")
        FluentButton(btn_frame, "Cancel", FluentColors.SIDEBAR, FluentColors.ROW_ALT,
                     dlg.destroy, width=100, height=32).pack(side="left", padx=8)

    def _delete_selected(self):
        sel = self.tree.selection()
        if not sel:
            messagebox.showinfo("Info", "Select a user first")
            return
        username = self.tree.item(sel[0])["values"][0]
        if messagebox.askyesno("Confirm", f"Delete user '{username}'?"):
            delete_user(username)
            self._refresh_users()

    def run(self):
        self.root.mainloop()


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Naven Verification Server")
    parser.add_argument("--port", type=int, default=8080, help="Server port")
    parser.add_argument("--no-gui", action="store_true", help="Run without GUI (headless)")
    args = parser.parse_args()

    print(f"Naven Verification Server")
    print(f"  Port: {args.port}")
    print(f"  Data: {DATA_FILE}")

    server_thread = threading.Thread(target=start_server, args=(args.port,), daemon=True)
    server_thread.start()

    if not args.no_gui:
        app = ServerGUI(args.port)
        app.run()
    else:
        print("Running headless. Press Ctrl+C to stop.")
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\nShutting down...")


if __name__ == "__main__":
    main()
