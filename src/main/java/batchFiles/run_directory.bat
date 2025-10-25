javac -d ..\bin\directory.pt.isec ..\pt\isec\directory\*.java ..\pt\isec\directory\threads\*.java ..\pt\isec\directory\protocol\*.java ..\pt\isec\directory\model\*.java ..\pt\isec\common\messages\*.java
pause

java -cp "..\bin\directory.pt.isec" pt.isec.directory.MainDirectory 9999 1024 4 17000
pause
