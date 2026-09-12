# DogBreeder

Fabric 1.21.11 client-side wolf breeding helper.

## Command
- `/breed` - toggle DogBreeder on/off
- `/breed on`
- `/breed off`
- `/breed status`

## What it does
- Works with your nearby tamed wolves.
- Automatically selects steak (cooked beef) in your hotbar.
- If steak is only in the main inventory, swaps a stack into your selected hotbar slot.
- Refills again when the held steak stack runs out.
- Feeds adult wolves to put them into breeding mode.
- After breeding, feeds baby wolves repeatedly to speed up growth.
- Restores the hotbar slot you were using when DogBreeder is turned off.

## Notes
- Wolves need to be within about 4.25 blocks of you.
- This does not move your player or pathfind to wolves.
- The server remains authoritative over breeding/item use, so normal Minecraft rules still apply.

## Requirements
- Minecraft Java 1.21.11
- Fabric Loader 0.18.2+
- Fabric API 0.139.4+1.21.11
- Java 21
