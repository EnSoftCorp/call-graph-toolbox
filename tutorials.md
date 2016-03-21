---
layout: page
title: Tutorials
permalink: /tutorials/
---

If you haven't already, [install](/call-graph-toolbox/install) the Call Graph Toolbox plugin into Eclipse.

First decide if you are analyzing a library (partial program analysis for a project with no main methods) or an application (whole program analysis). Navigate to `Eclipse` &gt; `Preferences` (or `Window` &gt; `Preferences`). Select `Call Graph Construction` and check the `Enable Library Call Graph Construction` checkbox depending on your analysis needs. 

![Preferences](../images/preferences.png)

To create a call graph for a project navigate to `Atlas` &gt; `Manage Project Settings`, select the project to analyze and press `OK`. Navigate to `Atlas` &gt; `Re-Map Workspace` to regenerate the program graph. Then navigate to `Atlas` &gt; `Open Smart View`. From the drop down menu select one of the nine call graph implementations `RA`, `CHA`, `RTA`, `FTA`, `MTA`, `ETA`, `XTA (classic)`, `XTA`, or `0-CFA`. Note that the `Call` Smart View is provided by default in Atlas and is equivalent to the `CHA` algorithm results provided by this plugin. When the call graph construction is finished the view will automatically update in response to a selection in the source editor or graph view windows.
![Smart View](../images/0CFA.png)

## 0-CFA (Points-to) Configuration
To utilize the 0-CFA call graph construction algorithm the [Points-to Toolbox](https://ensoftcorp.github.io/points-to-toolbox/) must be configured. Navigate to `Eclipse` &gt; `Preferences` (or `Window` &gt; `Preferences`). Select `Points-to Toolbox` and check the `Enable Jimple Points-to Analysis` checkbox.

Note: Currently "Jimple Points-to Analysis" analyzes both Java and Java bytecode.

![Preferences](../images/points-to-preferences.png)

If points-to analysis is enabled it will be invoked automatically after the workspace has been mapped.

## Algorithm Overview

A complete writeup and analysis of each algorithm is available at [ben-holland.com/call-graph-construction-algorithms-explained](https://ben-holland.com/call-graph-construction-algorithms-explained).

A summary of the tradeoffs for each algorithm can be found in the figure below.

![Tradeoff Summary](../images/summary.png)