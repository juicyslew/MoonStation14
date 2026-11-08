"""Generate the empty, reserved-volume GameTest template for the 64x6 lab."""
import gzip
import struct
from pathlib import Path


def name(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def tag(tag_type, key, payload):
    return bytes([tag_type]) + name(key) + payload


def main():
    # The lab origin is its floor plane; all writes are contained in Y=0..5.
    size = (64, 6, 64)
    # List payloads start with the element tag type, followed by list length.
    # StructureTemplate expects a compound palette entry and compound block
    # and entity entries; the existing clear templates use zero block entries.
    palette = bytes([10]) + struct.pack(">i", 1) + bytes([8]) + name("Name") + name("minecraft:air") + b"\0"
    blocks = bytes([10]) + struct.pack(">i", 0)
    entities = bytes([10]) + struct.pack(">i", 0)
    root = (b"\x0a\0\0" + tag(9, "size", bytes([3]) + struct.pack(">i", 3) + b"".join(struct.pack(">i", n) for n in size))
            + tag(9, "palette", palette) + tag(9, "blocks", blocks) + tag(9, "entities", entities)
            + tag(3, "DataVersion", struct.pack(">i", 3465)) + b"\0")
    out = Path("src/gametest/resources/data/moonstation14/structure/atmos_machinery_lab_clear.nbt")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(gzip.compress(root, mtime=0))


if __name__ == "__main__":
    main()
