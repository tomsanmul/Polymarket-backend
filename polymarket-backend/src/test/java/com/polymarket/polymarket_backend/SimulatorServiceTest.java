package com.polymarket.polymarket_backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.polymarket.polymarket_backend.dto.ClosedPositionDTO;
import com.polymarket.polymarket_backend.dto.OpenPositionRequest;
import com.polymarket.polymarket_backend.dto.PortfolioValueDTO;
import com.polymarket.polymarket_backend.dto.PositionDTO;
import com.polymarket.polymarket_backend.model.entity.PerformanceSnapshot;
import com.polymarket.polymarket_backend.model.entity.Position;
import com.polymarket.polymarket_backend.model.entity.SimulatorSession;
import com.polymarket.polymarket_backend.repository.PerformanceSnapshotRepository;
import com.polymarket.polymarket_backend.repository.PositionRepository;
import com.polymarket.polymarket_backend.repository.SimulatorSessionRepository;
import com.polymarket.polymarket_backend.service.PriceCacheService;
import com.polymarket.polymarket_backend.service.SimulatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class SimulatorServiceTest {

    @Mock private SimulatorSessionRepository sessionRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private PerformanceSnapshotRepository snapshotRepository;
    @Mock private PriceCacheService priceCacheService;

    private SimulatorService service;

    @Captor private ArgumentCaptor<SimulatorSession> sessionCaptor;
    @Captor private ArgumentCaptor<Position> positionCaptor;
    @Captor private ArgumentCaptor<PerformanceSnapshot> snapshotCaptor;

    @BeforeEach
    void setUp() {
        service = new SimulatorService(sessionRepository, positionRepository,
                snapshotRepository, priceCacheService);
    }

    @Test
    void start_createsSessionWith100kBalance() {
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.startSession();

        verify(sessionRepository, atLeastOnce()).save(sessionCaptor.capture());
        SimulatorSession saved = sessionCaptor.getAllValues().stream()
                .filter(s -> s.getBalance() == 100000.0)
                .findFirst().orElseThrow();
        assertTrue(saved.isEnabled());
        assertEquals(100000.0, saved.getBalance());
        assertEquals(0.0, saved.getUsedBalance());
    }

    @Test
    void start_resetsExistingSession() {
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.startSession();

        verify(positionRepository).deleteAll();
        verify(snapshotRepository).deleteAll();
        verify(sessionRepository).deleteAll();
    }

    @Test
    void openPosition_deductsBalance() {
        SimulatorSession session = createEnabledSession(100000, 0);
        when(sessionRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OpenPositionRequest req = new OpenPositionRequest();
        req.setMarketId("m1");
        req.setMarketQuestion("Will X happen?");
        req.setSide("YES");
        req.setAmount(1000);
        req.setEntryPrice(0.5);
        req.setOutcome("Yes");

        service.openPosition(req);

        verify(sessionRepository, atLeastOnce()).save(sessionCaptor.capture());
        SimulatorSession saved = sessionCaptor.getAllValues().get(sessionCaptor.getAllValues().size() - 1);
        assertEquals(99000.0, saved.getBalance(), 0.001);
        assertEquals(1000.0, saved.getUsedBalance(), 0.001);
    }

    @Test
    void openPosition_calculatesShares() {
        SimulatorSession session = createEnabledSession(100000, 0);
        when(sessionRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OpenPositionRequest req = new OpenPositionRequest();
        req.setMarketId("m1");
        req.setMarketQuestion("Will X happen?");
        req.setSide("YES");
        req.setAmount(500);
        req.setEntryPrice(0.25);
        req.setOutcome("Yes");

        service.openPosition(req);

        verify(positionRepository).save(positionCaptor.capture());
        Position saved = positionCaptor.getValue();
        assertEquals(2000, saved.getShares());
    }

    @Test
    void openPosition_throwsOnInsufficientBalance() {
        SimulatorSession session = createEnabledSession(100, 0);
        when(sessionRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(session));

        OpenPositionRequest req = new OpenPositionRequest();
        req.setAmount(200);
        req.setEntryPrice(0.5);

        assertThrows(IllegalArgumentException.class, () -> service.openPosition(req));
    }

    @Test
    void closePosition_calculatesPnL() {
        SimulatorSession session = createEnabledSession(50000, 50000);
        Position position = createOpenPosition("pos_1", 50000, 0.5, 100000);

        when(sessionRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(session));
        when(positionRepository.findById("pos_1")).thenReturn(Optional.of(position));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(priceCacheService.getPrice("m1", "YES")).thenReturn(1.0);

        ClosedPositionDTO result = service.closePosition("pos_1");

        assertEquals(50000.0, result.getPnl(), 0.001);
    }

    @Test
    void closePosition_restoresBalance() {
        SimulatorSession session = createEnabledSession(50000, 50000);
        Position position = createOpenPosition("pos_1", 50000, 0.5, 100000);

        when(sessionRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(session));
        when(positionRepository.findById("pos_1")).thenReturn(Optional.of(position));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(priceCacheService.getPrice("m1", "YES")).thenReturn(1.0);

        service.closePosition("pos_1");

        verify(sessionRepository, atLeastOnce()).save(sessionCaptor.capture());
        SimulatorSession saved = sessionCaptor.getAllValues().get(sessionCaptor.getAllValues().size() - 1);
        assertEquals(150000.0, saved.getBalance(), 0.001);
        assertEquals(0.0, saved.getUsedBalance(), 0.001);
    }

    @Test
    void closePosition_throwsIfNotFound() {
        when(positionRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.closePosition("nonexistent"));
    }

    @Test
    void closePosition_throwsIfAlreadyClosed() {
        Position position = createOpenPosition("pos_1", 50000, 0.5, 100000);
        position.setClosed(true);

        when(positionRepository.findById("pos_1")).thenReturn(Optional.of(position));

        assertThrows(IllegalStateException.class, () -> service.closePosition("pos_1"));
    }

    @Test
    void portfolioValue_computesCorrectly() {
        SimulatorSession session = createEnabledSession(40000, 60000);
        Position p1 = createOpenPosition("p1", 30000, 0.5, 60000);
        Position p2 = createOpenPosition("p2", 30000, 0.5, 60000);

        when(sessionRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(session));
        when(positionRepository.findByClosedFalse()).thenReturn(List.of(p1, p2));
        when(priceCacheService.getPrice(anyString(), anyString())).thenReturn(null);

        PortfolioValueDTO result = service.getPortfolioValue();

        assertEquals(100000.0, result.getPortfolioValue(), 0.001);
        assertEquals(0.0, result.getTotalPnl(), 0.001);
        assertEquals(60000.0, result.getOpenPositionsValue(), 0.001);
        assertEquals(40000.0, result.getBalance(), 0.001);
    }

    @Test
    void snapshot_respectsThrottle() {
        SimulatorSession session = createEnabledSession(100000, 0);
        session.setLastSnapshotTime(System.currentTimeMillis());

        when(sessionRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(session));

        service.recordSnapshot();

        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void getPerformanceHistory_filtersByRange() {
        long now = System.currentTimeMillis();
        PerformanceSnapshot oldSnap = new PerformanceSnapshot();
        oldSnap.setValue(90000);
        oldSnap.setTimestamp(now - 60L * 24 * 60 * 60 * 1000);
        PerformanceSnapshot newSnap = new PerformanceSnapshot();
        newSnap.setValue(110000);
        newSnap.setTimestamp(now);

        when(snapshotRepository.findAllByOrderByTimestampAsc())
                .thenReturn(List.of(oldSnap, newSnap));

        List<PerformanceSnapshot> result = service.getPerformanceHistory("1M");

        assertEquals(1, result.size());
        assertEquals(110000, result.get(0).getValue());
    }

    private SimulatorSession createEnabledSession(double balance, double usedBalance) {
        SimulatorSession session = new SimulatorSession();
        session.setId(UUID.randomUUID());
        session.setEnabled(true);
        session.setBalance(balance);
        session.setUsedBalance(usedBalance);
        session.setCreatedAt(Instant.now());
        return session;
    }

    private Position createOpenPosition(String id, double amount, double entryPrice, int shares) {
        Position p = new Position();
        p.setId(id);
        p.setMarketId("m1");
        p.setMarketQuestion("Will X happen?");
        p.setSide("YES");
        p.setAmount(amount);
        p.setEntryPrice(entryPrice);
        p.setShares(shares);
        p.setOutcome("Yes");
        p.setTimestamp(System.currentTimeMillis());
        p.setClosed(false);
        return p;
    }
}
