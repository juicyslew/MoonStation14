# Framed examine outline precision

The client now anchors framed outline geometry at the double-precision transformed
center of the storage bounds, subtracts the camera before converting that anchor
to float, and renders only small center-relative box coordinates. This preserves
the native render pose and applies the existing event PoseStack once. Ordinary
world/entity outlines are unchanged. The pinned Sable renderer package listing
contains region/dynamic-light renderers, not an outline helper accepting arbitrary
block-local geometry plus camera and event-base transforms. The narrow helper
therefore lives in `FrameExamineGeometry`.

`FrameExamineGeometryTest.cameraRelativeOutlinePreservesGlobalStorageCornersAndUnitSize`
checks the production matrix and local bounds consumed by `drawOutline`: all
corners under identity and rotated native poses, fractional pivot, nonzero camera,
and nonidentity event base. It verifies the unit extent survives where the former
global-float vertex coordinates collapse.

This addresses float precision in outline submission only; it does not claim to
resolve native-client movement/snapback or complete interactive overlay acceptance.
Those remain owner manual-validation gates.
