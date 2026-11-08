# Source-fidelity review

This is a review protocol, not an authoring specification or universal model template. Review the candidate against its **own** source family, current saved package, and requested change. An accepted asset (including the accepted mouse) can be a family-specific baseline, not invented approval for other identities. The accepted airlock reconstruction is a useful example of traceable source-to-3D decisions, not a mandatory creature anatomy/template.

## Review before a large batch

Require a small early design/contact review when a request changes identity, silhouette, anatomy, palette, scale, state interpretation, or motion. Before the worker builds a large family/batch, inspect a representative source-first contact sheet or editable prototype with original references and intended views. Record invariant anatomy/silhouette and high-risk assumptions; resolve blocking questions with the owner/source before batch production. If early review was skipped, call out the avoidable rework risk rather than retroactively describing it as approved.

## Source and identity evidence

- **Source pin and provenance:** identify exact original sprite/model/reference paths, family/species/variant and relevant native state/frame/duration. Record revision/source checkout pin where one is established; otherwise explicitly mark the pin unverified. Confirm attribution/license and a complete current saved dependency set. Do not infer licensing or approval from presence in a package.
- **Identity invariants:** write down distinguishing silhouette, proportions, head/body relationship, appendages, posture, eye count/shape/placement, signature markings, material/palette and functional visual motifs. Assess the named source, not generic polish or similarity to a neighboring model. Protect distinctive differences across the family; a shared rig may not erase identity.
- **Original-first comparison:** show source before candidate, at matching direction/orientation and a comparable camera, scale and gameplay distance. Include front/side/three-quarter views as the form requires; do not let a flattering beauty view hide wrong outline or confusing gameplay read. Then inspect the actual 3D candidate from multiple sides and at practical in-world distance.
- **Pixel-to-volume reasoning:** identify which marks are surface markings versus actual geometry, what anatomy is hidden, and how source projection maps to volume/UV. Record uncertain depth, occlusion, asymmetry, crop, scale conversion, camera and any invented detail as assumptions/departures. Never label inference as source fact.
- **Palette/material:** compare original RGB/alpha, contrast and recognizable material boundaries. Check important details under candidate lighting/texture conditions rather than silently beautifying, smoothing, recoloring, adding generic shine, or replacing a source motif with conventional 3D decoration.

Concrete workspace example: the current V05 Hamlet source-fidelity handoff calls out black/mint cut eyes with only a small white sliver; broad realistic human-like sclera/pupil/lid geometry was a mismatch, even if polished. This is a source-specific finding, not a rule to give unrelated creatures hamster eyes. A hand-drawn sprite also does not grant a new MIT license: preserve the recorded attribution/license and do not infer a license from availability.

## States and motion

Review actual idle, walk, turn, and signature-action evidence where the source supplies it; inspect relevant held/down/dead/alternate appearances only when in scope. Track independent source states and timings separately from static geometry. A single source frame does not prove a gait, and a texture timer is not a mechanical animation.

For each motion, label it **source-authentic** (supported by source frames/timing), **ported/adapted** (explicitly transformed to a Minecraft 3D visual), or **inferred/unsupported**. Compare cadence, planted/push contacts, facing, turn behavior, root motion, stride, limb coordination and character-specific signature. Reject an arbitrary generic walk reused across creatures when source proportions/locomotion require distinct movement; don't confuse noisier or faster motion with fidelity. Distinguish optical texture loops, skeletal/art clips, state selection and actual gameplay mechanics. Do not invent abilities, physics, AI, collision or gameplay from an art clip. If evidence is absent, say so and ask a precise question instead of filling the gap as fact.

## Actual-image evidence and decision

Independently open/review representative original-first and 3D images, not merely the HTML, generated report, filename, test result, or image hash. Note candidate revision/hash, exact inspected image paths, view/state and concrete source-relative findings. Record passes and misses per invariant; include blocking questions and unsupported views. Recheck actual images after a requested revision and bind the findings to the revised hashes, not the prior render. A hash establishes bytes only; it does not establish a human saw the image.

Return `needs_changes`, `ready_for_owner`, or unresolved owner questions using the current workflow tool schema. `ready_for_owner` means an independent review recommends that the owner inspect the recorded candidate; it is **not** artist/owner acceptance, import authorization, runtime installation, or automatic merge. Maintain fidelity review documentation/reports only; never edit, regenerate, recolor, relicense, import or alter guarded artwork while acting as reviewer. See [report template](review-report.md) and workspace source/package [preservation guide](../../../Asset%20Building%20Workspace/docs/development-workflow/README.md).
