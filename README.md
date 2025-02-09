# JORIC
**JOric** is an Oric emulator written in Java, using the libGDX cross-platform development framework, targeting primarily HTML5 and the web:

https://oric.games

The UI of JOric has been designed primarily with mobile devices in mind, so give it a try on your Android phone! 

## Features
- Intuitive, familiar, mobile-like UI, with game selection screens. Swipe/click to the right:
  
![](img/title_page_web_desktop.jpg)           |![](img/games_page_web_desktop.jpg) 
:-------------------------:|:-------------------------:

- Support for direct URL path access to individual games:
  - e.g. [https://oric.games/#/stormlord](https://oric.games/#/stormlord)
- Support for loading games via a ?url= request parameter:
  - e.g. [https://oric.games/?url=https://defence-force.org/files/im10.tap](https://oric.games/?url=https://defence-force.org/files/im10.tap)
  - e.g. [https://oric.games/?url=https://cdn.oric.org/games/software/z/zipnzap/ZIPNZAP.DSK](https://oric.games/?url=https://cdn.oric.org/games/software/z/zipnzap/ZIPNZAP.DSK)
- Support for games contained within ZIP files:
  - e.g. [https://oric.games/?url=https://defence-force.org/files/space1999-en.zip](https://oric.games/?url=https://defence-force.org/files/space1999-en.zip)
- Support for loading games attached to forum posts:
  - e.g. [https://oric.games/?url=https://forum.defence-force.org/download/file.php?id=4084](https://oric.games/?url=https://forum.defence-force.org/download/file.php?id=4084)
- Being a PWA (Progressive Web App), it can be installed locally to your device!
