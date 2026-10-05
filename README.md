# Post-contenido — Unidad 5: Integración en Aplicaciones Web

## Descripción
Repositorio del post-contenido de la Unidad 5 de Patrones de Diseño de Software. Un único proyecto Spring Boot (`reservas-labs-api`) para la reserva de laboratorios de cómputo de la universidad, estructurado en dos partes:
1. **Parte 1:** Una API REST construida con arquitectura en capas limpia (`Entity`, `Repository`, `Service`, `Controller`, `Exception`) sobre una base de datos relacional H2 embebida.
2. **Parte 2:** Una vista MVC clásica con Thymeleaf (`ReservaWebController`, plantillas HTML) que reutiliza exactamente la misma instancia de la capa de servicio (`ReservaService`), demostrando desacoplamiento y consistencia en el manejo de reglas de negocio y errores.

---

## Estructura del Proyecto

```
rozo-post1-u5/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/universidad/reservaslabs/
    │   │   ├── ReservasLabsApiApplication.java
    │   │   ├── model/
    │   │   │   ├── EstadoReserva.java
    │   │   │   ├── Laboratorio.java
    │   │   │   └── Reserva.java
    │   │   ├── repository/
    │   │   │   ├── LaboratorioRepository.java
    │   │   │   └── ReservaRepository.java
    │   │   ├── service/
    │   │   │   └── ReservaService.java
    │   │   ├── exception/
    │   │   │   ├── GlobalRestExceptionHandler.java
    │   │   │   ├── RecursoNoEncontradoException.java
    │   │   │   └── ReservaConflictException.java
    │   │   ├── controller/
    │   │   │   ├── LaboratorioController.java
    │   │   │   └── ReservaController.java
    │   │   └── web/
    │   │       ├── ReservaWebController.java
    │   │       └── ReservaWebExceptionHandler.java
    │   └── resources/
    │       ├── application.properties
    │       └── templates/reservas/
    │           ├── lista.html
    │           └── nueva.html
    └── test/
        └── java/com/universidad/reservaslabs/
            ├── ReservaControllerIntegrationTest.java
            └── ReservaWebControllerIntegrationTest.java
```

---

## Parte 1 — Repository, Service y Controller REST
`LaboratorioRepository` y `ReservaRepository` extienden `JpaRepository`. `ReservaRepository` añade una consulta JPQL personalizada (`buscarSolapamientos`) para filtrar en el motor de base de datos las reservas activas que se cruzan con el intervalo horario solicitado. `ReservaService` concentra toda la lógica de negocio sustancial: validación de horario de atención institucional (07:00 a 21:00), duración permitida (30 minutos a 3 horas), prohibición de reservas solapadas y rechazo de cancelaciones extemporáneas (reservas cuya hora de inicio ya transcurrió). 

`LaboratorioController` expone el catálogo básico de espacios físicos (`/api/laboratorios`), mientras que `ReservaController` expone los endpoints transaccionales (`/api/reservas`), asegurando que ninguna operación de reserva toque directamente el repositorio sin pasar por el servicio.

---

## Parte 2 — Vista MVC con Thymeleaf
`ReservaWebController` expone las rutas `/reservas` y `/reservas/nueva` utilizando Server-Side Rendering con Thymeleaf. Inyecta por constructor exactamente la **misma instancia bean** de `ReservaService` que utiliza `ReservaController` en la API REST, garantizando cero duplicación de lógica. 

`ReservaWebExceptionHandler` captura las excepciones de dominio (`ReservaConflictException`, `RecursoNoEncontradoException`) mediante `@ControllerAdvice(assignableTypes = ReservaWebController.class)`, redirigiendo de forma amigable al usuario con atributos flash (`RedirectAttributes`), mientras `GlobalRestExceptionHandler` continúa atendiendo a los clientes REST con respuestas JSON y códigos de estado HTTP precisos (`409`, `400`, `404`).

---

## Cómo ejecutar

### Prerrequisitos
- Java 17 o superior (`java -version`)
- Apache Maven 3.8+ (`mvn -version`)

### Compilación y Ejecución
```bash
# Compilar y empaquetar el proyecto
mvn clean package

# Iniciar la aplicación Spring Boot
mvn spring-boot:run
```

### URLs de Acceso
- **API REST:** `http://localhost:8080/api/reservas` y `http://localhost:8080/api/laboratorios`
- **Vista MVC (Thymeleaf):** `http://localhost:8080/reservas`
- **Formulario de Nueva Reserva:** `http://localhost:8080/reservas/nueva`
- **Consola H2 Database:** `http://localhost:8080/h2-console`
  - **JDBC URL:** `jdbc:h2:mem:reservas_labs_db`
  - **Driver Class:** `org.h2.Driver`
  - **User Name:** `sa`
  - **Password:** *(dejar vacío)*

---

## Endpoints y Rutas Disponibles

### API REST
| Método | Endpoint | Descripción | Códigos HTTP |
|---|---|---|---|
| `GET` | `/api/laboratorios` | Listar todos los laboratorios | `200 OK` |
| `GET` | `/api/laboratorios/{id}` | Consultar detalle de un laboratorio | `200 OK`, `404 Not Found` |
| `POST` | `/api/laboratorios` | Registrar nuevo laboratorio físico | `201 Created`, `400 Bad Request` |
| `GET` | `/api/reservas` | Listar todas las reservas registradas | `200 OK` |
| `GET` | `/api/reservas/{id}` | Consultar reserva por ID | `200 OK`, `404 Not Found` |
| `GET` | `/api/reservas/laboratorio/{id}` | Listar reservas por laboratorio | `200 OK` |
| `POST` | `/api/reservas` | Crear reserva (valida horario, duración y solapamiento) | `201 Created`, `400 Bad Request`, `409 Conflict` |
| `DELETE` | `/api/reservas/{id}` | Cancelar reserva (valida que no haya iniciado) | `204 No Content`, `404 Not Found`, `409 Conflict` |

### Rutas Web MVC (Thymeleaf)
| Método | Ruta | Vista / Acción |
|---|---|---|
| `GET` | `/reservas` | Renderiza `reservas/lista.html` con tabla de reservas y botón cancelar |
| `GET` | `/reservas/nueva` | Renderiza `reservas/nueva.html` con formulario y select de laboratorios |
| `POST` | `/reservas` | Procesa creación vía `ReservaService.crear()`, redirige a `/reservas` o a `/reservas/nueva` con flash error |
| `POST` | `/reservas/{id}/cancelar` | Procesa cancelación vía `ReservaService.cancelar()`, redirige a `/reservas` |

---

## Decisiones de diseño

### Punto de decisión 1 — Ubicación de la validación de solapamiento
- **Dilema arquitectónico:** ¿Dónde debe realizarse la detección de solapamiento de horarios? ¿Debe `ReservaService` traer a memoria JVM todas las reservas existentes del laboratorio y comparar rangos temporales mediante bucles Java, o debe delegarse el filtrado a una consulta JPQL en `ReservaRepository`?
- **Decisión adoptada:** Se implementó una clara división de responsabilidades:
  1. **El Repository responde una pregunta de datos pura:** `ReservaRepository.buscarSolapamientos` ejecuta una cláusula JPQL (`r.laboratorio.id = :laboratorioId AND r.estado <> CANCELADA AND r.inicio < :fin AND r.fin > :inicio`) filtrando los registros directamente en el motor de base de datos H2. Esta consulta escala de forma eficiente ($O(1)$ en uso de memoria y transferencia de red respecto al historial acumulado de reservas del laboratorio), aprovechando índices relacionales.
  2. **El Service responde la pregunta de negocio:** `ReservaService.crear()` es quien interpreta el resultado de dicha consulta. Si la lista no está vacía, el Service aplica la regla del dominio: rechazar la creación lanzando `ReservaConflictException` con un mensaje claro y expresivo.
- **¿Qué pasaría si el Controller llamara directamente a `buscarSolapamientos()`?**
  Si `ReservaController` o `ReservaWebController` invocaran directamente a `ReservaRepository.buscarSolapamientos()` sin pasar por el Service, ocurrirían fallas arquitectónicas severas:
  - **Fuga de lógica de negocio a la capa de transporte:** El Controller se vería forzado a interpretar si la lista devuelta representa un conflicto o no, decidiendo estados y mensajes de error.
  - **Duplicación de código:** Tanto el controlador REST como el controlador MVC tendrían que copiar y pegar la misma condición de comprobación.
  - **Pérdida de integridad:** Cualquier otro punto de entrada al sistema (por ejemplo, procesos por lotes, mensajería asíncrona o importadores) podría omitir la validación de solapamiento si esta no está encapsulada en la capa Service.
  - **Service anémico:** La capa de servicio quedaría degradada a un passthrough inservible, perdiendo su razón de ser.

---

### Punto de decisión 2 — Reglas con y sin apoyo del Repository
- **Criterio de clasificación:**
  1. **Reglas con apoyo del Repository (dependientes de datos externos/persistidos):** La verificación de solapamiento no puede ser resuelta examinando únicamente la entidad `Reserva` entrante, porque dicha reserva desconoce qué otras citas han sido agendadas por terceros en el mismo laboratorio. Requiere indefectiblemente consultar el estado actual de la persistencia mediante `ReservaRepository`.
  2. **Reglas sin apoyo del Repository (reglas intrínsecas del dominio):** La validación del horario de atención de la universidad (07:00 a 21:00) y el rango permitido de duración (mínimo 30 minutos, máximo 3 horas) son invariantes universales que dependen exclusivamente de los atributos `inicio` y `fin` de la reserva entrante.
- **Justificación:** `validarHorarioYDuracion` vive enteramente en `ReservaService` utilizando la API nativa `java.time` sin interactuar con la base de datos. Consultar la base de datos para validar condiciones que solo conciernen al objeto en memoria añadiría sobrecarga de red y latencia de I/O innecesarias, acoplaría el repositorio con lógica estática y degradaría el rendimiento general de la aplicación.

---

### Punto de decisión 3 — Cómo comparten Service el Controller MVC y el REST
- **Mecanismo de compartición:** `ReservaController` ([ReservaController.java](file:///C:/Users/Public/Dev/Patrones%20de%20dise%C3%B1o/rozo-post1-u5/src/main/java/com/universidad/reservaslabs/controller/ReservaController.java#L16)) y `ReservaWebController` ([ReservaWebController.java](file:///C:/Users/Public/Dev/Patrones%20de%20dise%C3%B1o/rozo-post1-u5/src/main/java/com/universidad/reservaslabs/web/ReservaWebController.java#L16)) reciben mediante inyección de dependencias por constructor exactamente el mismo bean singleton de Spring: `ReservaService`.
- **Alternativa descartada y consecuencias:**
  - Se descartó crear un "ReservaWebService" o duplicar la lógica de negocio dentro de `ReservaWebController`. 
  - Si en el futuro la universidad decide modificar las políticas de reserva (por ejemplo, permitir horarios hasta las 22:00, ampliar la duración a 4 horas o admitir solapamientos bajo autorización especial), mantener dos servicios obligaría a actualizar el código en dos sitios distintos, arriesgando inconsistencias operativas donde la API REST aplicara una regla y la interfaz web aplicara otra.
  - Al inyectar la misma clase `ReservaService`, ambas superficies consumen de forma unificada e idéntica la lógica de validación, garantizando el principio DRY (*Don't Repeat Yourself*).

---

### Punto de decisión 4 — Manejo de errores consistente entre MVC y REST
- **Dilema:** ¿Por qué no unificar el manejo de errores en un solo `@ControllerAdvice` global?
- **Decisión adoptada:** Se implementaron dos manejadores especializados orientados a superficies de presentación diferentes, compartiendo exactamente el mismo vocabulario de excepciones de dominio (`ReservaConflictException` y `RecursoNoEncontradoException`):
  1. `GlobalRestExceptionHandler`: Anotado con `@RestControllerAdvice(annotations = RestController.class)`. Intercepta peticiones REST y produce un cuerpo de respuesta JSON (`Map<String, String>`) con códigos de estado HTTP semánticos (`409 Conflict` para solapamientos, `400 Bad Request` para horarios/duraciones inválidas y validaciones de campo, `404 Not Found` para laboratorios/reservas inexistentes).
  2. `ReservaWebExceptionHandler`: Anotado con `@ControllerAdvice(assignableTypes = ReservaWebController.class)`. Intercepta peticiones provenientes del navegador web, captura las mismas excepciones de negocio y genera una redirección HTTP (`redirect:/reservas/nueva` o `redirect:/reservas`) inyectando el mensaje explicativo en atributos flash (`redirect.addFlashAttribute("error", ex.getMessage())`) para que la plantilla Thymeleaf lo renderice en color rojo.
- **Beneficio arquitectónico:** Se preserva el principio de responsabilidad única (SRP). Si se utilizara un solo manejador global, este tendría que inspeccionar condicionalmente headers HTTP (`Accept`, `Content-Type`) o nombres de métodos para discernir si debe retornar JSON o una vista, introduciendo complejidad innecesaria y acoplamiento entre tecnologías de renderizado.

---

## Herramientas utilizadas
- **Lenguaje:** Java 17 (OpenJDK / JetBrains Runtime)
- **Framework:** Spring Boot 3.2.5
- **Módulos Spring:** Spring Web (REST), Spring MVC, Spring Data JPA, Jakarta Bean Validation, Thymeleaf Starter
- **Base de Datos:** H2 Database (embebida en memoria)
- **Construcción y Dependencias:** Apache Maven 3.9+
- **Pruebas Automatizadas:** JUnit 5, MockMvc, Spring Boot Test
- **Control de Versiones:** Git & GitHub

---

## Conclusiones
El desarrollo de este post-contenido demostró en la práctica el inmenso valor de una arquitectura en capas rigurosa al desacoplar las reglas esenciales del dominio respecto a los mecanismos de entrega (REST vs. MVC Thymeleaf). El desafío técnico más enriquecedor consistió en discernir los límites exactos de cada responsabilidad: delegar en el motor relacional el filtrado pesado de solapamientos mediante JPQL para optimizar recursos, pero reservando el criterio de decisión y lanzamiento de excepciones estrictamente en la capa de servicio. La reutilización exitosa de `ReservaService` entre controladores de naturaleza dispar evidenció que cuando la lógica de negocio se aísla de los detalles de transporte, el sistema gana mantenibilidad, consistencia funcional y facilidad de evolución a largo plazo.
