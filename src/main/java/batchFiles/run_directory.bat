javac -d ..\bin\directory.pt.isec ^
  -cp "..\lib\jansi-2.4.0.jar" ^
  ..\pt\isec\directory\*.java ^
  ..\pt\isec\directory\threads\*.java ^
  ..\pt\isec\directory\core\*.java ^
  ..\pt\isec\common\messages\*.java ^
  ..\pt\isec\common\dto\auth\*.java ^
  ..\pt\isec\common\util\*.java

pause

java --enable-native-access=ALL-UNNAMED ^
 -cp "..\bin\directory.pt.isec;..\lib\jansi-2.4.0.jar" ^
 pt.isec.directory.MainDirectory 9999 1024 3 17000

pause
