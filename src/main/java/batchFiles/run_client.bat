javac -d ..\bin\client.pt.isec ..\pt\isec\client\*.java ..\pt\isec\common\messages\*.java
pause

java -cp "..\bin\client.pt.isec" pt.isec.client.MainClient localhost 9999
pause
