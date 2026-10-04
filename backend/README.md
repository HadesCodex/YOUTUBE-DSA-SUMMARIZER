# DSA Summariser - Backend

Run: `mvn spring-boot:run`  (http://localhost:8080)

Test:
curl -X POST http://localhost:8080/api/summarise \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.youtube.com/watch?v=dQw4w9WgXcQ","transcript":"paste any text here"}'
