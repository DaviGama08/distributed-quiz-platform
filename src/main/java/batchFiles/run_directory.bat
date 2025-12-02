javac -d ..\bin\directory.pt.isec ^
  ..\pt\isec\directory\*.java ^
  ..\pt\isec\directory\threads\*.java ^
  ..\pt\isec\directory\core\*.java ^
  ..\pt\isec\common\messages\*.java ^
  ..\pt\isec\common\dto\auth\*.java ^
  ..\pt\isec\common\util\*.java

pause

java -cp "..\bin\directory.pt.isec" pt.isec.directory.MainDirectory 9999 1024 3 17000

pause