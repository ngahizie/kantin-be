#!/usr/bin/env bash
# ============================================================================
# install-backup-timer.sh — Pasang timer backup di mesin INI (portabel)
# ----------------------------------------------------------------------------
# KENAPA ADA: berkas unit systemd butuh path absolut, sedangkan lokasi repo
# berbeda di tiap mesin (laptop dev, server, mesin teman). Script ini mengganti
# placeholder __ROOT__ dengan path repo saat ini, lalu memasang unit-nya.
#
# Pakai:
#   scripts/install-backup-timer.sh          # pasang + aktifkan timer
#   scripts/install-backup-timer.sh --uninstall
#
# Tidak perlu sudo (systemd user unit).
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/scripts/systemd"
DEST="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"

if [[ "${1:-}" == "--uninstall" ]]; then
    systemctl --user disable --now kantin-backup.timer 2>/dev/null || true
    rm -f "$DEST/kantin-backup.service" "$DEST/kantin-backup.timer"
    systemctl --user daemon-reload
    echo "Timer backup dilepas dari $DEST"
    exit 0
fi

mkdir -p "$DEST"

for unit in kantin-backup.service kantin-backup.timer; do
    sed "s|__ROOT__|$ROOT|g" "$SRC/$unit" > "$DEST/$unit"
    echo "terpasang: $DEST/$unit"
done

systemctl --user daemon-reload
systemctl --user enable --now kantin-backup.timer

echo
echo "Timer aktif. Jadwal berikutnya:"
systemctl --user list-timers kantin-backup.timer --all --no-pager | head -3
echo
echo "Uji sekarang: systemctl --user start kantin-backup.service"
echo "Lihat log:    journalctl --user -u kantin-backup.service -n 20"