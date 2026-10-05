# Upstream atmosphere code attribution

The gas-mixture, transport, excited-group, reaction, and hotspot behavior in this parity pass is adapted from Space Station 14, revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`:
https://github.com/space-wizards/space-station-14/tree/c9df5ef5d675b0d1d226828bddf6b78c28502d91

Relevant pinned source paths include `Content.Shared/Atmos/`, `Content.Server/Atmos/EntitySystems/`, `Content.Server/Atmos/Reactions/`, and `Resources/Prototypes/Atmospherics/reactions.yml`. These contain the gas-mixture and transport algorithms, excited groups, reactions, hotspots, and reaction prototype data adapted in this parity pass. Java implementations adapt the upstream algorithms to Minecraft geometry, volume, and server integration. No code or assets were imported verbatim, and no audio or sprite assets were imported.

Upstream code license (`LICENSE.TXT` at the pinned revision):

```
MIT License

Copyright (c) 2017-2026 Space Wizards Federation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
