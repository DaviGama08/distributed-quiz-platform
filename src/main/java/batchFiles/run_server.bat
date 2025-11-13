javac -d ..\bin\server.pt.isec ^
  ..\pt\isec\server\*.java ^
  ..\pt\isec\server\threads\*.java ^
  ..\pt\isec\server\threads\client\*.java ^
  ..\pt\isec\server\db\*.java ^
  ..\pt\isec\server\db\dao\*.java ^
  ..\pt\isec\server\services\auth\*.java ^
  ..\pt\isec\server\services\config\*.java ^
  ..\pt\isec\server\services\session\*.java ^
  ..\pt\isec\common\messages\*.java ^
  ..\pt\isec\common\dto\auth\*.java ^
  ..\pt\isec\common\model\user\*.java
pause

java --enable-native-access=ALL-UNNAMED ^
 -cp "..\bin\server.pt.isec;..\..\resources;..\lib\sqlite-jdbc-3.45.2.0.jar;..\lib\slf4j-api-2.0.13.jar;..\lib\slf4j-simple-2.0.13.jar" ^
 pt.isec.server.MainServer localhost 9999 PROJECT 192.168.1.50 5003 17003

pause




