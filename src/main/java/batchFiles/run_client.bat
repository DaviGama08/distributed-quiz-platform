javac ^
 -d ..\bin\client.pt.isec ^
 -cp "..\bin\client.pt.isec;..\lib\*;..\lib\javafx\lib\*" ^
 ..\pt\isec\client\*.java ^
 ..\pt\isec\client\threads\*.java ^
 ..\pt\isec\client\services\*.java ^
 ..\pt\isec\client\ui\controller\*.java ^
 ..\pt\isec\client\ui\view\*.java ^
 ..\pt\isec\common\messages\*.java ^
 ..\pt\isec\common\dto\auth\*.java ^
 ..\pt\isec\common\dto\answer\*.java ^
 ..\pt\isec\common\dto\question\*.java ^
 ..\pt\isec\common\model\question\*.java ^
 ..\pt\isec\common\model\user\*.java

pause

java ^
 --module-path ..\lib\javafx\lib ^
 --add-modules javafx.controls,javafx.fxml ^
 -cp "..\bin\client.pt.isec;..\..\resources;..\lib\*;..\lib\javafx\lib\*" ^
 pt.isec.client.ClientApplication

pause
