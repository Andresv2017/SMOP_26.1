#!/usr/bin/env bash
# Cross-checks the bones an animation file drives against the bones a model actually declares.
#
# Why this exists: on 1.20.1 KeyframeAnimations.animate resolved bones through
# getAnyDescendantWithName -> Optional, and silently skipped any it could not find. 26.1 bakes
# animations instead (AnimationDefinition#bake -> KeyframeAnimation.bake), which THROWS:
#
#   IllegalArgumentException: Cannot animate <bone>, which does not exist in model
#
# So a Blockbench export carrying leftover channels for bones the model does not have was harmless
# on 1.20.1 and is a hard client crash here. The crash is lazy — it fires the first time that
# particular clip plays on that particular model — which makes it easy to miss in a quick playtest.
#
# Run this for every model/animation pair BEFORE porting a mob.
#
# Usage:
#   tools/check-bones.sh <Model.java> <Animations.java> [MoreAnimations.java ...]
#
# Exit code 1 if any animated bone is missing from the model.

set -u

if [ "$#" -lt 2 ]; then
    echo "usage: $0 <Model.java> <Animations.java> [...]" >&2
    exit 2
fi

model="$1"
shift

bones=$(grep -hoE 'addOrReplaceChild\("[a-zA-Z0-9_]+"' "$model" \
    | sed 's/addOrReplaceChild("//; s/"//' | sort -u)

animated=$(grep -hoE 'addAnimation\("[a-zA-Z0-9_]+"' "$@" \
    | sed 's/addAnimation("//; s/"//' | sort -u)

missing=$(comm -13 <(echo "$bones") <(echo "$animated"))

if [ -z "$missing" ]; then
    echo "OK  $(basename "$model"): every animated bone exists in the model."
    exit 0
fi

echo "CRASH  $(basename "$model"): animated bones missing from the model:" >&2
echo "$missing" | sed 's/^/  - /' >&2
echo >&2
echo "Clips referencing them:" >&2
for anim in "$@"; do
    awk -v bad="$(echo "$missing" | tr '\n' '|' | sed 's/|$//')" '
        /public static final AnimationDefinition/ {
            split($0, a, "AnimationDefinition "); split(a[2], b, " "); clip = b[1]
        }
        {
            if (match($0, /addAnimation\("[a-zA-Z0-9_]+"/)) {
                bone = substr($0, RSTART + 14, RLENGTH - 15)
                if (bone ~ "^(" bad ")$") print "  - " FILENAME_SHORT ": " clip " -> " bone
            }
        }
    ' FILENAME_SHORT="$(basename "$anim")" "$anim" >&2
done
exit 1
