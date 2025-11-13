javac -d ..\bin\directory.pt.isec ^
  ..\pt\isec\directory\*.java ^
  ..\pt\isec\directory\threads\*.java ^
  ..\pt\isec\directory\protocol\*.java ^
  ..\pt\isec\directory\service\*.java ^
  ..\pt\isec\common\messages\*.java ^
  ..\pt\isec\common\dto\auth\*.java
pause

java -cp "..\bin\directory.pt.isec" pt.isec.directory.MainDirectory 9999 1024 3 17000
