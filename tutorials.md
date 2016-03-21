---
layout: page
title: Tutorials
permalink: /tutorials/
---

If you haven't already, [install](/call-graph-toolbox/install) the Call Graph Toolbox plugin into Eclipse.

First navigate to `Atlas` &gt; `Manage Project Settings`, select the project to analyze and press `OK`. Navigate to `Atlas` &gt; `Re-Map Workspace` to regenerate the program graph.

## 0-CFA (Points-to) Configuration
To utilize the 0-CFA call graph construction algorithm the `Points-to Toolbox` must be configured. Navigate to `Eclipse` &gt; `Preferences` (or `Window` &gt; `Preferences`). Select `Points-to Toolbox` and check the `Enable Jimple Points-to Analysis` checkbox.

Note: Currently "Jimple Points-to Analysis" analyzes both Java and Java bytecode.

![Preferences](../images/points-to-preferences.png)

If points-to analysis is enabled it will be invoked automatically after the workspace has been mapped.