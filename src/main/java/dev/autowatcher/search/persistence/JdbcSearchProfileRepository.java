package dev.autowatcher.search.persistence;

import dev.autowatcher.search.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSearchProfileRepository implements SearchProfileRepository {
    private final JdbcTemplate jdbc;
    public JdbcSearchProfileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final RowMapper<SearchProfile> MAPPER = (rs, rowNum) -> map(rs);

    @Override public void save(SearchProfile p) {
        jdbc.update("""
            INSERT INTO search_profiles(id,name,enabled,brand,model,year_from,year_to,
              price_from,price_to,mileage_to,region,sources,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
              name=excluded.name, enabled=excluded.enabled, brand=excluded.brand,
              model=excluded.model, year_from=excluded.year_from, year_to=excluded.year_to,
              price_from=excluded.price_from, price_to=excluded.price_to,
              mileage_to=excluded.mileage_to, region=excluded.region,
              sources=excluded.sources, updated_at=excluded.updated_at
            """, p.id().toString(), p.name(), p.enabled() ? 1 : 0,
            p.criteria().brand(), p.criteria().model(), p.criteria().yearFrom(),
            p.criteria().yearTo(), p.criteria().priceFrom(), p.criteria().priceTo(),
            p.criteria().mileageTo(), p.criteria().region(),
            p.sources().stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
            p.createdAt().toString(), p.updatedAt().toString());
    }
    @Override public List<SearchProfile> findAll() {
        return jdbc.query("SELECT * FROM search_profiles ORDER BY created_at DESC", MAPPER);
    }
    @Override public Optional<SearchProfile> findById(UUID id) {
        return jdbc.query("SELECT * FROM search_profiles WHERE id=?", MAPPER, id.toString()).stream().findFirst();
    }
    @Override public boolean delete(UUID id) {
        jdbc.update("DELETE FROM test_notification_receipts WHERE profile_id=?", id.toString());
        jdbc.update("DELETE FROM listing_matches WHERE profile_id=?", id.toString());
        jdbc.update("DELETE FROM notification_outbox WHERE profile_id=?", id.toString());
        jdbc.update("DELETE FROM profile_listings WHERE profile_id=?", id.toString());
        jdbc.update("DELETE FROM monitoring_runs WHERE profile_id=?", id.toString());
        return jdbc.update("DELETE FROM search_profiles WHERE id=?", id.toString()) > 0;
    }
    private static Integer integer(ResultSet rs, String column) throws SQLException {
        Number n = (Number) rs.getObject(column); return n == null ? null : n.intValue();
    }
    private static Long longValue(ResultSet rs, String column) throws SQLException {
        Number n = (Number) rs.getObject(column); return n == null ? null : n.longValue();
    }
    private static SearchProfile map(ResultSet rs) throws SQLException {
        Set<SourceType> sources = Arrays.stream(rs.getString("sources").split(","))
            .map(SourceType::valueOf).collect(Collectors.toSet());
        SearchCriteria criteria = new SearchCriteria(rs.getString("brand"), rs.getString("model"),
            integer(rs, "year_from"), integer(rs, "year_to"),
            longValue(rs, "price_from"), longValue(rs, "price_to"),
            integer(rs, "mileage_to"), rs.getString("region"));
        return new SearchProfile(UUID.fromString(rs.getString("id")), rs.getString("name"),
            rs.getInt("enabled") != 0, criteria, sources,
            Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")));
    }
}
