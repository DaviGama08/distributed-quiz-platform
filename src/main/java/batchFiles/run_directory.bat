javac -d ..\bin\directory.pt.isec ..\pt\isec\directory\*.java ..\pt\isec\directory\Threads\*.java ..\pt\isec\common\messages\*.java
pause

java -cp "..\bin\directory.pt.isec" pt.isec.directory.MainDirectory 9999 1024 65535 17000
pause
