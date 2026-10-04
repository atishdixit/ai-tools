@echo off
setlocal
rem Regenerates company-data\employee-handbook.pdf (a demo PDF with a real text layer).
rem Needs only JDK 21; PDFBox is copied from the project's own dependencies.
cd /d "%~dp0.."
call "%~dp0..\mvnw.cmd" -B -q dependency:copy-dependencies -DincludeArtifactIds=pdfbox,pdfbox-io,fontbox,commons-logging -DoutputDirectory=target\pdfbox-libs
if errorlevel 1 (
    echo Could not fetch the PDFBox libraries.
    exit /b 1
)
java -cp "target\pdfbox-libs\*" tools\GenerateHandbookPdf.java
exit /b %ERRORLEVEL%
