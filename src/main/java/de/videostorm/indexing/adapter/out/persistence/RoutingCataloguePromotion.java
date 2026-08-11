package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.indexing.application.port.out.CataloguePromotion;
import de.videostorm.sources.domain.SourceType;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link CataloguePromotion} the run lifecycle sees: it holds one {@link CataloguePromotor} per
 * {@link SourceType} and dispatches the swap to the matching one. A type with no promotor is a no-op,
 * mirroring the scan side. Because each swap is scoped to its own type's tables, promoting one type
 * leaves the other's live catalogue exactly as it was.
 */
@Repository
class RoutingCataloguePromotion implements CataloguePromotion {

    private final Map<SourceType, CataloguePromotor> byType;

    RoutingCataloguePromotion(List<CataloguePromotor> promotors) {
        Map<SourceType, CataloguePromotor> map = new EnumMap<>(SourceType.class);
        for (CataloguePromotor promotor : promotors) {
            map.put(promotor.type(), promotor);
        }
        this.byType = map;
    }

    @Override
    public void promote(SourceType type) {
        CataloguePromotor promotor = byType.get(type);
        if (promotor != null) {
            promotor.promote();
        }
    }
}
