"""Minimal Source RCON client for a local Minecraft server.

Usage:
    py tools/rcon.py [--host 127.0.0.1] [--port 25575] [--password-file PATH] "command" ["command" ...]

The password is read from --password-file, the RCON_PASSWORD environment variable, or the server's
server.properties (--properties PATH), in that order, so it never has to appear on a command line.
Each command's reply is printed after a "> command" line. Long replies arrive in several packets;
an empty marker command is sent after each one so the reply is only printed once it is complete.
"""
import argparse
import os
import socket
import struct
import sys

LOGIN, COMMAND, RESPONSE = 3, 2, 0


def _packet(request_id: int, kind: int, body: str) -> bytes:
    payload = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def _read_exact(sock: socket.socket, size: int) -> bytes:
    data = b""
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise ConnectionError("RCON connection closed")
        data += chunk
    return data


def _read(sock: socket.socket) -> tuple[int, int, str]:
    (length,) = struct.unpack("<i", _read_exact(sock, 4))
    request_id, kind = struct.unpack("<ii", _read_exact(sock, 8))
    body = _read_exact(sock, length - 8)[:-2]
    return request_id, kind, body.decode("utf-8", errors="replace")


def password_from(args: argparse.Namespace) -> str:
    if args.password_file:
        return open(args.password_file, encoding="utf-8").read().strip()
    if os.environ.get("RCON_PASSWORD"):
        return os.environ["RCON_PASSWORD"]
    if args.properties:
        for line in open(args.properties, encoding="utf-8"):
            if line.startswith("rcon.password="):
                return line.split("=", 1)[1].strip()
    sys.exit("no RCON password: pass --password-file, set RCON_PASSWORD, or pass --properties")


def run(host: str, port: int, password: str, commands: list[str], timeout: float) -> int:
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.sendall(_packet(1, LOGIN, password))
        request_id, _, _ = _read(sock)
        if request_id == -1:
            print("RCON login refused (wrong password)", file=sys.stderr)
            return 2
        next_id = 10
        for command in commands:
            command_id, marker_id = next_id, next_id + 1
            next_id += 2
            sock.sendall(_packet(command_id, COMMAND, command))
            # Minecraft answers an unknown packet type with a fixed reply, which marks the end.
            sock.sendall(_packet(marker_id, RESPONSE, ""))
            reply = []
            while True:
                request_id, _, body = _read(sock)
                if request_id == marker_id:
                    break
                if request_id == command_id:
                    reply.append(body)
            print(f"> {command}")
            print("".join(reply) if reply else "(no reply)")
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password-file")
    parser.add_argument("--properties", help="server.properties to read rcon.password from")
    parser.add_argument("--timeout", type=float, default=120.0)
    parser.add_argument("commands", nargs="+")
    args = parser.parse_args()
    sys.exit(run(args.host, args.port, password_from(args), args.commands, args.timeout))


if __name__ == "__main__":
    main()
