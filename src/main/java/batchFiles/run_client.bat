javac -d ..\bin\client.pt.isec -cp ..\bin\client.pt.isec ..\pt\isec\client\*.java ..\pt\isec\client\threads\*.java ..\pt\isec\client\services\*.java ..\pt\isec\common\messages\*.java ..\pt\isec\common\dto\auth\*.java ..\pt\isec\common\dto\answer\*.java ..\pt\isec\common\dto\question\*.java ..\pt\isec\common\model\common\*.java ..\pt\isec\common\model\question\*.java ..\pt\isec\common\model\answer\*.java ..\pt\isec\common\model\user\*.java ..\pt\isec\common\model\config\*.java
pause

java -cp "..\bin\client.pt.isec" pt.isec.client.MainClient localhost 9999
pause
