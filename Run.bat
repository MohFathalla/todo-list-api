cd todo-api
mvn clean install
mvn spring-boot:run
start "" cmd /c "timeout /t 10 /nobreak >nul && start http://localhost:8080/swagger-ui/index.html"
pause
