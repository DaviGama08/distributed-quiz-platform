javac -d ..\bin\server.pt.isec ^
  -cp "..\lib\sqlite-jdbc-3.45.2.0.jar;..\lib\slf4j-api-2.0.13.jar;..\lib\slf4j-simple-2.0.13.jar;..\lib\jansi-2.4.0.jar" ^
  ..\pt\isec\server\core\*.java ^
  ..\pt\isec\server\*.java ^
  ..\pt\isec\server\threads\*.java ^
  ..\pt\isec\server\db\*.java ^
  ..\pt\isec\server\services\auth\*.java ^
  ..\pt\isec\server\services\question\*.java ^
  ..\pt\isec\common\model\user\*.java ^
  ..\pt\isec\common\model\question\*.java ^
  ..\pt\isec\common\messages\*.java ^
  ..\pt\isec\common\dto\auth\*.java ^
  ..\pt\isec\common\dto\question\*.java ^
  ..\pt\isec\common\dto\answer\*.java ^
  ..\pt\isec\common\util\*.java

pause

java --enable-native-access=ALL-UNNAMED ^
 -cp "..\bin\server.pt.isec;..\..\resources;..\lib\sqlite-jdbc-3.45.2.0.jar;..\lib\slf4j-api-2.0.13.jar;..\lib\slf4j-simple-2.0.13.jar;..\lib\jansi-2.4.0.jar" ^
 pt.isec.server.MainServer 10.84.85.89 9999 PROJECT 192.168.1.233 5011 17011

pause
