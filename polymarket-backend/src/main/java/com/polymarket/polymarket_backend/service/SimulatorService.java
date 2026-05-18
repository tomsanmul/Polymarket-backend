package com.polymarket.polymarket_backend.service;

import com.polymarket.polymarket_backend.dto.ClosedPositionDTO;
import com.polymarket.polymarket_backend.dto.OpenPositionRequest;
import com.polymarket.polymarket_backend.dto.PortfolioValueDTO;
import com.polymarket.polymarket_backend.dto.PositionDTO;
import com.polymarket.polymarket_backend.dto.SimulatorStateDTO;
import com.polymarket.polymarket_backend.model.entity.PerformanceSnapshot;
import com.polymarket.polymarket_backend.model.entity.Position;
import com.polymarket.polymarket_backend.model.entity.SimulatorSession;
import com.polymarket.polymarket_backend.repository.PerformanceSnapshotRepository;
import com.polymarket.polymarket_backend.repository.PositionRepository;
import com.polymarket.polymarket_backend.repository.SimulatorSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class SimulatorService {

    private static final double DEFAULT_BALANCE = 100_000.0;
    private static final long SNAPSHOT_THROTTLE_MS = 10_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SimulatorSessionRepository sessionRepository;
    private final PositionRepository positionRepository;
    private final PerformanceSnapshotRepository snapshotRepository;
    private final PriceCacheService priceCacheService;

    public SimulatorService(SimulatorSessionRepository sessionRepository,
                            PositionRepository positionRepository,
                            PerformanceSnapshotRepository snapshotRepository,
                            PriceCacheService priceCacheService) {
        this.sessionRepository = sessionRepository;
        this.positionRepository = positionRepository;
        this.snapshotRepository = snapshotRepository;
        this.priceCacheService = priceCacheService;
    }

    public SimulatorStateDTO startSession() {
        positionRepository.deleteAll();
        snapshotRepository.deleteAll();
        sessionRepository.deleteAll();

        SimulatorSession session = new SimulatorSession();
        session.setEnabled(true);
        session.setBalance(DEFAULT_BALANCE);
        session.setUsedBalance(0.0);
        session.setCreatedAt(Instant.now());
        session.setLastSnapshotTime(null);
        session = sessionRepository.save(session);

        recordSnapshotForSession(session, true);

        return toStateDTO(session);
    }

    public void stopSession() {
        Optional<SimulatorSession> existing = sessionRepository.findFirstByEnabledTrue();
        if (existing.isEmpty()) {
            return;
        }
        positionRepository.deleteAll();
        snapshotRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Transactional(readOnly = true)
    public SimulatorStateDTO getState() {
        SimulatorSession session = getActiveSession();
        return toStateDTO(session);
    }

    public PositionDTO openPosition(OpenPositionRequest req) {
        SimulatorSession session = getActiveSession();

        if (req.getAmount() > session.getBalance()) {
            throw new IllegalArgumentException("Insufficient balance: required " + req.getAmount()
                    + " but available " + session.getBalance());
        }

        String id = "pos_" + System.currentTimeMillis() + "_" + randomAlphanumeric(4);
        int shares = (int) Math.floor(req.getAmount() / req.getEntryPrice());

        Position position = new Position();
        position.setId(id);
        position.setMarketId(req.getMarketId());
        position.setMarketQuestion(req.getMarketQuestion());
        position.setSide(req.getSide());
        position.setAmount(req.getAmount());
        position.setEntryPrice(req.getEntryPrice());
        position.setShares(shares);
        position.setOutcome(req.getOutcome());
        position.setTimestamp(System.currentTimeMillis());
        position.setClosed(false);
        position.setSession(session);
        position = positionRepository.save(position);

        session.setBalance(session.getBalance() - req.getAmount());
        session.setUsedBalance(session.getUsedBalance() + req.getAmount());
        sessionRepository.save(session);

        priceCacheService.refreshPrice(req.getMarketId());
        recordSnapshotForSession(session, false);

        return toPositionDTO(position);
    }

    public ClosedPositionDTO closePosition(String positionId) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new NoSuchElementException("Position not found: " + positionId));

        if (position.isClosed()) {
            throw new IllegalStateException("Position already closed: " + positionId);
        }

        double currentPrice = resolveCurrentPrice(position);
        double currentValue = position.getShares() * currentPrice;
        double pnl = currentValue - position.getAmount();

        SimulatorSession session = getActiveSession();
        session.setBalance(session.getBalance() + currentValue);
        session.setUsedBalance(session.getUsedBalance() - position.getAmount());
        sessionRepository.save(session);

        position.setClosed(true);
        position.setClosePrice(currentPrice);
        position.setCloseValue(currentValue);
        position.setPnl(pnl);
        position.setCloseTime(System.currentTimeMillis());
        position = positionRepository.save(position);

        recordSnapshotForSession(session, false);

        return toClosedPositionDTO(position);
    }

    @Transactional(readOnly = true)
    public PortfolioValueDTO getPortfolioValue() {
        SimulatorSession session = getActiveSession();
        List<Position> openPositions = positionRepository.findByClosedFalse();

        double openPositionsValue = 0.0;
        for (Position p : openPositions) {
            double price = resolveCurrentPrice(p);
            openPositionsValue += p.getShares() * price;
        }

        double portfolioValue = session.getBalance() + openPositionsValue;
        double totalPnl = portfolioValue - DEFAULT_BALANCE;

        return new PortfolioValueDTO(portfolioValue, totalPnl, openPositionsValue, session.getBalance());
    }

    @Transactional(readOnly = true)
    public List<PerformanceSnapshot> getPerformanceHistory(String range) {
        List<PerformanceSnapshot> snapshots = snapshotRepository.findAllByOrderByTimestampAsc();
        if (range == null || range.isBlank()) {
            return snapshots;
        }

        long cutoff = switch (range.toUpperCase()) {
            case "1M" -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
            case "3M" -> System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000;
            case "6M" -> System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000;
            case "1Y" -> System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000;
            case "2Y" -> System.currentTimeMillis() - 730L * 24 * 60 * 60 * 1000;
            default -> 0L;
        };

        long finalCutoff = cutoff;
        return snapshots.stream()
                .filter(s -> s.getTimestamp() >= finalCutoff)
                .toList();
    }

    public void recordSnapshot() {
        Optional<SimulatorSession> optSession = sessionRepository.findFirstByEnabledTrue();
        if (optSession.isEmpty()) {
            return;
        }
        recordSnapshotForSession(optSession.get(), false);
    }

    private void recordSnapshotForSession(SimulatorSession session, boolean force) {
        long now = System.currentTimeMillis();
        Long lastTime = session.getLastSnapshotTime();
        if (!force && lastTime != null && (now - lastTime) < SNAPSHOT_THROTTLE_MS) {
            return;
        }

        double portfolioValue = computePortfolioValue(session);
        PerformanceSnapshot snapshot = new PerformanceSnapshot();
        snapshot.setPortfolioValue(portfolioValue);
        snapshot.setTimestamp(now);
        snapshot.setSession(session);
        snapshotRepository.save(snapshot);

        session.setLastSnapshotTime(now);
        sessionRepository.save(session);
    }

    private double computePortfolioValue(SimulatorSession session) {
        List<Position> openPositions = positionRepository.findByClosedFalse();
        double openValue = 0.0;
        for (Position p : openPositions) {
            openValue += p.getShares() * resolveCurrentPrice(p);
        }
        return session.getBalance() + openValue;
    }

    private double resolveCurrentPrice(Position position) {
        Double cached = priceCacheService.getPrice(position.getMarketId(), position.getSide());
        if (cached != null) {
            return cached;
        }
        return position.getEntryPrice() > 0 ? position.getEntryPrice() : 0.5;
    }

    private SimulatorSession getActiveSession() {
        return sessionRepository.findFirstByEnabledTrue()
                .orElseThrow(() -> new IllegalStateException("No active simulator session"));
    }

    private SimulatorStateDTO toStateDTO(SimulatorSession session) {
        List<Position> openPositions = positionRepository.findByClosedFalse();
        List<Position> closedPositions = positionRepository.findByClosedTrueOrderByCloseTimeDesc();

        SimulatorStateDTO dto = new SimulatorStateDTO();
        dto.setEnabled(session.isEnabled());
        dto.setBalance(session.getBalance());
        dto.setUsedBalance(session.getUsedBalance());
        dto.setPositions(openPositions.stream().map(this::toPositionDTO).toList());
        dto.setHistory(closedPositions.stream().map(this::toClosedPositionDTO).toList());
        return dto;
    }

    private PositionDTO toPositionDTO(Position p) {
        PositionDTO dto = new PositionDTO();
        dto.setId(p.getId());
        dto.setMarketId(p.getMarketId());
        dto.setMarketQuestion(p.getMarketQuestion());
        dto.setSide(p.getSide());
        dto.setAmount(p.getAmount());
        dto.setEntryPrice(p.getEntryPrice());
        dto.setShares(p.getShares());
        dto.setOutcome(p.getOutcome());
        dto.setTimestamp(p.getTimestamp());
        dto.setClosed(p.isClosed());
        dto.setClosePrice(p.getClosePrice());
        dto.setCloseValue(p.getCloseValue());
        dto.setPnl(p.getPnl());
        dto.setCloseTime(p.getCloseTime());
        dto.setCurrentPrice(resolveCurrentPrice(p));
        return dto;
    }

    private ClosedPositionDTO toClosedPositionDTO(Position p) {
        ClosedPositionDTO dto = new ClosedPositionDTO();
        dto.setId(p.getId());
        dto.setMarketId(p.getMarketId());
        dto.setMarketQuestion(p.getMarketQuestion());
        dto.setSide(p.getSide());
        dto.setAmount(p.getAmount());
        dto.setEntryPrice(p.getEntryPrice());
        dto.setShares(p.getShares());
        dto.setOutcome(p.getOutcome());
        dto.setTimestamp(p.getTimestamp());
        dto.setClosePrice(p.getClosePrice() != null ? p.getClosePrice() : 0.0);
        dto.setCloseValue(p.getCloseValue() != null ? p.getCloseValue() : 0.0);
        dto.setPnl(p.getPnl() != null ? p.getPnl() : 0.0);
        dto.setCloseTime(p.getCloseTime() != null ? p.getCloseTime() : 0L);
        return dto;
    }

    private static String randomAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
