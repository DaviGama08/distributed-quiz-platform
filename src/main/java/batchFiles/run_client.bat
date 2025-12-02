javac ^
 -d ..\bin\client.pt.isec ^
 -cp "..\bin\client.pt.isec;..\lib\*;..\lib\javafx\lib\*" ^
 ..\pt\isec\client\*.java ^
 ..\pt\isec\client\threads\*.java ^
 ..\pt\isec\client\core\*.java ^
 ..\pt\isec\client\services\*.java ^
 ..\pt\isec\client\ui\*.java ^
 ..\pt\isec\client\ui\auth\*.java ^
 ..\pt\isec\client\ui\student\*.java ^
 ..\pt\isec\client\ui\teacher\*.java ^
 ..\pt\isec\client\ui\util\*.java ^
 ..\pt\isec\client\ui\util\dialogs\*.java ^
 ..\pt\isec\common\util\*.java ^
 ..\pt\isec\common\messages\*.java ^
 ..\pt\isec\common\dto\auth\*.java ^
 ..\pt\isec\common\dto\answer\*.java ^
 ..\pt\isec\common\dto\question\*.java ^
 ..\pt\isec\common\model\question\*.java ^
 ..\pt\isec\common\model\user\*.java ^
 ..\pt\isec\common\util\*.java

pause

java ^
 --module-path ..\lib\javafx\lib ^
 --add-modules javafx.controls,javafx.fxml ^
 -cp "..\bin\client.pt.isec;..\..\resources;..\lib\*;..\lib\javafx\lib\*" ^
 pt.isec.client.ClientApplication

pause
