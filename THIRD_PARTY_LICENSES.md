# Third-Party Licenses

This project bundles or adapts code from the following third-party
software. The respective copyright notices and license texts are
reproduced below in full, as required by their license terms.

---

## Screencopy (ImUrX/screencopy)

The screenshot-to-clipboard feature inside
`vorga.phazeclient.implement.features.modules.other.ChatHelper`
(plus the supporting mixins
`vorga.phazeclient.mixins.NativeImageGetColorInvoker` and
`vorga.phazeclient.mixins.ScreenshotRecorderScreencopyMixin`) is
adapted from [screencopy](https://github.com/ImUrX/screencopy) by
ImUrX, used under the MIT License.

```
The MIT License (MIT)

Copyright (c) 2021 ImUrX contributors

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

---

## Inf Chat History (xBackpack/InfChatHistory)

The Longer Chat History feature inside
`vorga.phazeclient.implement.features.modules.other.ChatHelper`
(plus the supporting mixin
`vorga.phazeclient.mixins.ChatHudHistoryLimitMixin`) is adapted from
[InfChatHistory](https://github.com/xBackpack/InfChatHistory) by
xBackpack, released under CC0 1.0 Universal (Public Domain
Dedication). The upstream `ChatComponent` mixin targets Forge yarn
mappings; the Phaze port retargets the Yarn `ChatHud` constant uses
(visible-message cap, message cap, and message-history cap) and adds a
configurable slider plus a per-module toggle.

```
Creative Commons Legal Code

CC0 1.0 Universal

Affirmer hereby overtly, fully, permanently, irrevocably and
unconditionally waives, abandons, and surrenders all of Affirmer's
Copyright and Related Rights and associated claims and causes of
action, whether now known or unknown, in the Work in all territories
worldwide, for the maximum duration provided by applicable law or
treaty, in any current or future medium and for any number of copies,
and for any purpose whatsoever, including without limitation
commercial, advertising or promotional purposes.

THE WORK IS PROVIDED "AS-IS" AND THE AFFIRMER MAKES NO REPRESENTATIONS
OR WARRANTIES OF ANY KIND CONCERNING THE WORK, EXPRESS, IMPLIED,
STATUTORY OR OTHERWISE, INCLUDING WITHOUT LIMITATION WARRANTIES OF
TITLE, MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
NON-INFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS, ACCURACY,
OR THE PRESENT OR ABSENCE OF ERRORS, WHETHER OR NOT DISCOVERABLE.

Full text: https://creativecommons.org/publicdomain/zero/1.0/legalcode
```

---

## No Hand Sway (O3kar/no-hand-sway)

The No Hand Sway feature inside
`vorga.phazeclient.implement.features.modules.other.ChangeHand`
(plus the supporting mixin
`vorga.phazeclient.mixins.HeldItemRendererNoSwayMixin`) is adapted
from [no-hand-sway](https://github.com/O3kar/no-hand-sway) by O3kar,
licensed under the Apache License, Version 2.0.

```
Copyright (c) O3kar

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
