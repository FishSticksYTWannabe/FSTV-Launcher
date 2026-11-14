Icon pack - drop your own tile art in this folder.

The launcher shows whatever image you put here instead of the stock app icon.
Setup pushes every file in here to the TV; you can swap any tile by re-running setup.

File format
  png, jpg, jpeg, or webp
  312 x 176 is the native tile size, but anything close to 16:9 works - the
  launcher fits the image to the tile without cropping.

File name = app package name (or short key for the special tiles)
  Netflix              com.netflix.ninja.png
  YouTube (Fire TV)    com.amazon.firetv.youtube.png
  Prime Video          com.amazon.firebat.png
  Disney+              com.disney.disneyplus.png
  Hotstar              in.startv.hotstar.png
  SmartTube            com.liskovsoft.smarttubetv.beta.png
  Amazon Appstore      com.amazon.venezia.png

Special (built-in) tiles
  Settings             settings.png
  Fire TV Home         stock.png
  Inputs               livetv.png

Tips
  - Put brand/logo over a solid or gradient background; the tile has rounded corners.
  - To find any other app's package name, run:
        adb -s <TV_IP>:5555 shell pm list packages
  - Delete a file here and re-run setup to go back to the stock icon.
