javac -d ..\bin\server.pt.isec ..\pt\isec\server\*.java ..\pt\isec\common\messages\*.java
pause

java -cp "..\bin\server.pt.isec" pt.isec.server.MainServer localhost 9999 0
pause
