---
description: Build inlined html file for all themes, and updates the Themmes folder
---

Review the clusyter-widgets folder if there are changes in the files for any of the subfolders (each one is a specific theme).
For each folder with changes do the following
1. execute 'npm run build'
2. if the theme is not 'air-control' do copy the generated inlined html file from dist folder to the cluster-widgets/Themes/<<folder>> where <<folder> would be the respective folder for that theme (it matches the name
3. update the cluster-widgets/Themes/<<folder>>/theme.xml and increase the version in 0.0.1