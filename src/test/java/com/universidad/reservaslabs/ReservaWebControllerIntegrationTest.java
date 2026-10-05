package com.universidad.reservaslabs;

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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReservaWebControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    @DisplayName("MVC: Listar reservas retorna vista reservas/lista")
    void testListarReservasView() throws Exception {
        mockMvc.perform(get("/reservas"))
                .andExpect(status().isOk())
                .andExpect(view().name("reservas/lista"))
                .andExpect(model().attributeExists("reservas"));
    }

    @Test
    @DisplayName("MVC: Formulario nueva reserva retorna vista reservas/nueva")
    void testFormularioNuevaReserva() throws Exception {
        mockMvc.perform(get("/reservas/nueva"))
                .andExpect(status().isOk())
                .andExpect(view().name("reservas/nueva"))
                .andExpect(model().attributeExists("reserva"))
                .andExpect(model().attributeExists("laboratorios"));
    }

    @Test
    @DisplayName("MVC: Crear reserva válida redirige a /reservas con mensaje flash")
    void testCrearReservaWebValida() throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime fin = inicio.plusHours(2);

        mockMvc.perform(post("/reservas")
                        .param("laboratorio.id", lab.getId().toString())
                        .param("nombreSolicitante", "María Gómez")
                        .param("correoSolicitante", "maria@udes.edu.co")
                        .param("inicio", inicio.toString())
                        .param("fin", fin.toString())
                        .param("motivo", "Taller Algoritmos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservas"))
                .andExpect(flash().attribute("mensaje", "Reserva creada correctamente"));
    }

    @Test
    @DisplayName("MVC: Reserva solapada redirige a /reservas/nueva con error flash")
    void testReservaSolapadaWebRedirigeConError() throws Exception {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime fin = inicio.plusHours(2);

        // Crear primera reserva
        mockMvc.perform(post("/reservas")
                        .param("laboratorio.id", lab.getId().toString())
                        .param("nombreSolicitante", "María Gómez")
                        .param("correoSolicitante", "maria@udes.edu.co")
                        .param("inicio", inicio.toString())
                        .param("fin", fin.toString())
                        .param("motivo", "Taller Algoritmos"))
                .andExpect(status().is3xxRedirection());

        // Segunda reserva solapada
        mockMvc.perform(post("/reservas")
                        .param("laboratorio.id", lab.getId().toString())
                        .param("nombreSolicitante", "Pedro Pérez")
                        .param("correoSolicitante", "pedro@udes.edu.co")
                        .param("inicio", inicio.plusHours(1).toString())
                        .param("fin", fin.plusHours(1).toString())
                        .param("motivo", "Práctica Redes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservas/nueva"))
                .andExpect(flash().attribute("error", containsString("ya tiene una reserva en ese horario")));
    }
}
