package com.polymarket.polymarket_backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.polymarket.polymarket_backend.controller.SimulatorController;
import com.polymarket.polymarket_backend.dto.ClosedPositionDTO;
import com.polymarket.polymarket_backend.dto.OpenPositionRequest;
import com.polymarket.polymarket_backend.dto.PortfolioValueDTO;
import com.polymarket.polymarket_backend.dto.PositionDTO;
import com.polymarket.polymarket_backend.dto.SimulatorStateDTO;
import com.polymarket.polymarket_backend.model.entity.PerformanceSnapshot;
import com.polymarket.polymarket_backend.service.PriceCacheService;
import com.polymarket.polymarket_backend.service.SimulatorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(SimulatorController.class)
class SimulatorControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SimulatorService simulatorService;
    @MockitoBean private PriceCacheService priceCacheService;

    @Test
    void startSession_returnsState() throws Exception {
        SimulatorStateDTO state = new SimulatorStateDTO();
        state.setEnabled(true);
        state.setBalance(100000);
        when(simulatorService.startSession()).thenReturn(state);

        mockMvc.perform(post("/api/simulator/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.balance").value(100000));
    }

    @Test
    void stopSession_returnsState() throws Exception {
        SimulatorStateDTO state = new SimulatorStateDTO();
        state.setEnabled(false);
        org.mockito.Mockito.doNothing().when(simulatorService).stopSession();
        when(simulatorService.getState()).thenReturn(state);

        mockMvc.perform(post("/api/simulator/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void getState_returnsState() throws Exception {
        SimulatorStateDTO state = new SimulatorStateDTO();
        state.setEnabled(true);
        when(simulatorService.getState()).thenReturn(state);

        mockMvc.perform(get("/api/simulator/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void openPosition_returns201() throws Exception {
        PositionDTO dto = new PositionDTO();
        dto.setId("pos_1234_abcd");
        dto.setMarketId("m1");
        when(simulatorService.openPosition(any())).thenReturn(dto);

        String body = "{\"marketId\":\"m1\",\"side\":\"YES\",\"amount\":100,\"entryPrice\":0.5,\"outcome\":\"Yes\"}";

        mockMvc.perform(post("/api/simulator/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("pos_1234_abcd"));
    }

    @Test
    void getOpenPositions_returnsList() throws Exception {
        SimulatorStateDTO state = new SimulatorStateDTO();
        PositionDTO dto = new PositionDTO();
        dto.setId("pos_1");
        state.setPositions(List.of(dto));
        when(simulatorService.getState()).thenReturn(state);

        mockMvc.perform(get("/api/simulator/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pos_1"));
    }

    @Test
    void closePosition_returnsClosedPosition() throws Exception {
        ClosedPositionDTO dto = new ClosedPositionDTO();
        dto.setId("pos_1");
        dto.setPnl(5000);
        when(simulatorService.closePosition("pos_1")).thenReturn(dto);

        mockMvc.perform(delete("/api/simulator/positions/pos_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pos_1"))
                .andExpect(jsonPath("$.pnl").value(5000));
    }

    @Test
    void getPerformance_returnsSnapshots() throws Exception {
        PerformanceSnapshot snap = new PerformanceSnapshot();
        snap.setPortfolioValue(110000);
        snap.setTimestamp(1000L);
        when(simulatorService.getPerformanceHistory(null)).thenReturn(List.of(snap));

        mockMvc.perform(get("/api/simulator/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].portfolioValue").value(110000));
    }

    @Test
    void getPortfolioValue_returnsPortfolio() throws Exception {
        PortfolioValueDTO dto = new PortfolioValueDTO(110000, 10000, 50000, 60000);
        when(simulatorService.getPortfolioValue()).thenReturn(dto);

        mockMvc.perform(get("/api/simulator/portfolio-value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioValue").value(110000))
                .andExpect(jsonPath("$.totalPnl").value(10000));
    }
}
