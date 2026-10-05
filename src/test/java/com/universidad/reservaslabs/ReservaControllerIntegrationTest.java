package com.universidad.reservaslabs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import com.universidad.reservaslabs.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReservaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LaboratorioRepository labRepo;

    @Autowired
    private ReservaRepository reservaRepo;

    private Laboratorio lab;

    @BeforeEach
    void setUp() {
        reservaRepo.deleteAll();
        labRepo.deleteAll();

        lab = new Laboratorio();
        lab.setNombre("Lab. Cómputo 3");
        lab.setUbicacion("Bloque B, piso 2");
        lab.setCapacidad(30);
        lab.setTipo("COMPUTO");
        lab = labRepo.save(lab);
    }

    @Test
    @DisplayName("Checkpoint 1: Crear reserva válida retorna 201 Created")
    void testCrearReservaValida() throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime fin = inicio.plusHours(2);

        Reserva reserva = new Reserva();
        reserva.setLaboratorio(lab);
        reserva.setNombreSolicitante("Ana Torres");
        reserva.setCorreoSolicitante("ana@udes.edu.co");
        reserva.setInicio(inicio);
        reserva.setFin(fin);
        reserva.setMotivo("Práctica de Bases de Datos");

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserva)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.nombreSolicitante").value("Ana Torres"));
    }

    @Test
    @DisplayName("Checkpoint 2: Reserva solapada retorna 409 Conflict")
    void testReservaSolapadaRetorna409() throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime fin = inicio.plusHours(2);

        Reserva r1 = new Reserva();
        r1.setLaboratorio(lab);
        r1.setNombreSolicitante("Ana Torres");
        r1.setCorreoSolicitante("ana@udes.edu.co");
        r1.setInicio(inicio);
        r1.setFin(fin);
        r1.setMotivo("Práctica de Bases de Datos");

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r1)))
                .andExpect(status().isCreated());

        // Intento solapado: 10:00 a 12:00 (se cruza con 09:00 a 11:00)
        Reserva r2 = new Reserva();
        r2.setLaboratorio(lab);
        r2.setNombreSolicitante("Luis Gómez");
        r2.setCorreoSolicitante("luis@udes.edu.co");
        r2.setInicio(inicio.plusHours(1));
        r2.setFin(fin.plusHours(1));
        r2.setMotivo("Proyecto de Redes");

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("ya tiene una reserva en ese horario")));
    }

    @Test
    @DisplayName("Checkpoint 3: Reserva fuera de horario de atención retorna 400 Bad Request")
    void testReservaFueraDeHorarioRetorna400() throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1).withHour(22).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime fin = inicio.plusHours(1);

        Reserva reserva = new Reserva();
        reserva.setLaboratorio(lab);
        reserva.setNombreSolicitante("Carlos Ruiz");
        reserva.setCorreoSolicitante("carlos@udes.edu.co");
        reserva.setInicio(inicio);
        reserva.setFin(fin);
        reserva.setMotivo("Estudio nocturno");

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserva)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("horario de atención")));
    }

    @Test
    @DisplayName("Checkpoint 3b: Reserva con duración fuera de rango retorna 400 Bad Request")
    void testReservaDuracionInvalidaRetorna400() throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime fin = inicio.plusMinutes(15); // Menor a 30 minutos

        Reserva reserva = new Reserva();
        reserva.setLaboratorio(lab);
        reserva.setNombreSolicitante("Carlos Ruiz");
        reserva.setCorreoSolicitante("carlos@udes.edu.co");
        reserva.setInicio(inicio);
        reserva.setFin(fin);
        reserva.setMotivo("Prueba corta");

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserva)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("duración de la reserva debe estar entre 30 minutos y 3 horas")));
    }

    @Test
    @DisplayName("Catálogo de Laboratorios: listar y crear")
    void testLaboratoriosCRUD() throws Exception {
        mockMvc.perform(get("/api/laboratorios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Lab. Cómputo 3"));

        mockMvc.perform(get("/api/laboratorios/" + lab.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidad").value(30));
    }
}
