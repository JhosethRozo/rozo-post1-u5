# Post-contenido — Unidad 5: Integración en Aplicaciones Web

## Descripción
Repositorio del post-contenido de la Unidad 5 de Patrones de Diseño de Software. Un único proyecto Spring Boot (`reservas-labs-api`) para la gestión y reserva de laboratorios de cómputo de la universidad, estructurado en arquitectura por capas (Entity, Repository, Service, Controller REST) sobre base de datos embebida H2.

## Parte 1 — Repository, Service y Controller REST
`LaboratorioRepository` y `ReservaRepository` extienden `JpaRepository`; `ReservaRepository` agrega una consulta JPQL propia (`buscarSolapamientos`) para detectar solapamientos de horario eficientemente en la base de datos. `ReservaService` concentra las reglas de negocio reales (detección de solapamientos, validación de horario de atención 07:00–21:00, duración permitida entre 30 min y 3 h, y restricción de cancelación tardía). `LaboratorioController` y `ReservaController` exponen los endpoints bajo `/api/laboratorios` y `/api/reservas`.

## Cómo ejecutar
Requisitos: Java 17+ y Maven 3.8+.

```bash
# Compilar y empaquetar
mvn clean package

# Ejecutar la aplicación
mvn spring-boot:run
```

- API REST: `http://localhost:8080/api/reservas` y `http://localhost:8080/api/laboratorios`
- Consola H2: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:reservas_labs_db`
  - Usuario: `sa`
  - Contraseña: *(vacía)*

## Endpoints REST Disponibles

| Método | Endpoint | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/api/laboratorios` | Listar todos los laboratorios | `200 OK` |
| `GET` | `/api/laboratorios/{id}` | Obtener laboratorio por ID | `200 OK`, `404 Not Found` |
| `POST` | `/api/laboratorios` | Crear nuevo laboratorio | `201 Created`, `400 Bad Request` |
| `GET` | `/api/reservas` | Listar todas las reservas | `200 OK` |
| `GET` | `/api/reservas/{id}` | Obtener reserva por ID | `200 OK`, `404 Not Found` |
| `GET` | `/api/reservas/laboratorio/{id}` | Listar reservas por laboratorio | `200 OK` |
| `POST` | `/api/reservas` | Crear nueva reserva (aplica reglas de negocio) | `201 Created`, `400 Bad Request`, `409 Conflict` |
| `DELETE` | `/api/reservas/{id}` | Cancelar reserva (no cancela si ya inició) | `204 No Content`, `404 Not Found`, `409 Conflict` |

## Decisiones de diseño

### Punto de decisión 1 — Ubicación de la validación de solapamiento de horarios
**Dilema:** ¿Dónde debe vivir la validación de solapamiento de horarios? ¿Enteramente en el Service trayendo a memoria todas las reservas del laboratorio y filtrando con bucles Java, o delegando el filtrado a una consulta JPQL en el Repository?

**Decisión adoptada:** Se implementó una separación estricta de responsabilidades entre el Repository y el Service:
1. **El Repository responde una pregunta de datos (`¿Qué registros coinciden con este criterio temporal?`):** La consulta JPQL `buscarSolapamientos` en `ReservaRepository` ejecuta la condición `r.inicio < :fin AND r.fin > :inicio AND r.estado <> CANCELADA` directamente en el motor de base de datos H2. Esto garantiza escalabilidad óptima ($O(1)$ en transferencia de red/memoria respecto al histórico total de reservas del laboratorio), aprovechando índices de base de datos sin sobrecargar la memoria JVM.
2. **El Service responde una pregunta de negocio (`¿Se permite o no crear la reserva bajo el estado actual del sistema?`):** `ReservaService` invoca `buscarSolapamientos(...)`, evalúa el resultado y, de no estar vacío, decide la consecuencia de negocio: denegar la operación lanzando una excepción de dominio específica (`ReservaConflictException`).

**¿Qué pasaría si el Controller llamara directamente a `buscarSolapamientos()` sin pasar por el Service?**
Si el Controller invocara directamente el Repository, se produciría una fuga de lógica de negocio hacia la capa de presentación (HTTP). El Controller tendría que inspeccionar si la lista resultante está vacía o no, interpretar el significado de negocio de dicha lista y decidir qué código de error retornar. Esto acoplaría el transporte HTTP con las reglas del dominio, violaría el principio de responsabilidad única (SRP), impediría reutilizar la validación en otras superficies (como la vista MVC de Thymeleaf en la Parte 2 o tareas batch) y convertiría al Service en un passthrough prescindible o anémico.

### Punto de decisión 2 — Reglas con y sin apoyo del Repository
**Criterio de clasificación:**
- **Reglas dependientes del estado global / datos persistidos (con apoyo del Repository):** La regla de no solapamiento requiere contrastar la nueva reserva contra el histórico de reservas existentes de ese mismo laboratorio. Dado que el objeto que llega en la petición desconoce las reservas creadas por otros usuarios, es obligatorio consultar a la base de datos vía `ReservaRepository.buscarSolapamientos`.
- **Reglas intrínsecas o puramente de dominio (sin apoyo del Repository):** Las validaciones de horario de atención (07:00 a 21:00) y duración permitida (mínimo 30 minutos, máximo 3 horas) son invariantes que dependen de forma exclusiva de los campos `inicio` y `fin` de la reserva a registrar. No necesitan conocer ninguna otra fila de la base de datos ni consultar tablas auxiliares. Por tanto, viven 100% en `ReservaService.validarHorarioYDuracion()` evaluadas mediante la API `java.time` pura.

Involucrar al Repository o a la base de datos en reglas intrínsecas agregaría latencia de I/O innecesaria, ensuciaría la interfaz del repositorio y violaría la cohesión de la capa de servicio.

## Herramientas utilizadas
- Java 17
- Spring Boot 3.2.5
- Spring Data JPA / Hibernate
- H2 Database (en memoria)
- Jakarta Bean Validation
- Lombok
- Apache Maven
