package com.poc.redis.service;

package com.example.cache.service;

import com.example.cache.client.BackendApiClient;
import com.example.cache.dto.FetchResponseDTO;
import com.example.cache.dto.NomineeDto;
import com.example.cache.dto.RoleDto;
import com.example.cache.model.Field;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CacheWrapperService {

    private static final Logger log =
            LoggerFactory.getLogger(CacheWrapperService.class);

    public static final String CLIENT_STORE_KEY = "CLIENT_STORE";
    public static final String POLICY_STORE_KEY = "POLICY_STORE";

    private final BackendApiClient backendApiClient;

    public CacheWrapperService(BackendApiClient backendApiClient) {
        this.backendApiClient = backendApiClient;
    }

    // ============================================================
    // PUBLIC METHODS
    // ============================================================

    public FetchResponseDTO getClientData(
            HttpSession session,
            String clientId,
            Set<Field> requestedFields) {

        return executePipeline(
                session,
                CLIENT_STORE_KEY,
                clientId,
                requestedFields);
    }

    public FetchResponseDTO getPolicyData(
            HttpSession session,
            String policyId,
            Set<Field> requestedFields) {

        return executePipeline(
                session,
                POLICY_STORE_KEY,
                policyId,
                requestedFields);
    }

    // ============================================================
    // MAIN PIPELINE
    // ============================================================

    private FetchResponseDTO executePipeline(
            HttpSession session,
            String storeKey,
            String id,
            Set<Field> requestedFields) {

        CacheContext context =
                readCache(session, storeKey, id);

        Set<Field> fieldsToFetch =
                determineMissingFields(
                        session,
                        storeKey,
                        context,
                        requestedFields);

        FetchResponseDTO apiResponse = null;

        if (!fieldsToFetch.isEmpty()) {
            apiResponse =
                    callBackendApi(
                            storeKey,
                            id,
                            fieldsToFetch);
        }

        if (apiResponse != null) {
            updateCache(
                    session,
                    storeKey,
                    id,
                    context,
                    fieldsToFetch,
                    apiResponse);
        }

        return buildResponse(
                session,
                storeKey,
                context,
                requestedFields);
    }

    // ============================================================
    // 1. READ CACHE
    // ============================================================

    @SuppressWarnings("unchecked")
    private CacheContext readCache(
            HttpSession session,
            String storeKey,
            String id) {

        CacheContext context = new CacheContext();

        try {

            if (session == null) {
                return context;
            }

            Map<String, Map<Field, List<?>>> masterStore =
                    (Map<String, Map<Field, List<?>>>) session
                            .getAttribute(storeKey);

            context.masterStore = masterStore;

            if (masterStore != null) {

                Map<Field, List<?>> cachedEntry =
                        masterStore.get(id);

                if (cachedEntry != null) {
                    context.localCache = cachedEntry;
                }
            }

        } catch (Exception ex) {

            context.cacheReadFailed = true;

            log.error(
                    "Cache read failed. store={}, id={}",
                    storeKey,
                    id,
                    ex);
        }

        return context;
    }

    // ============================================================
    // 2. DETERMINE MISSING FIELDS
    // ============================================================

    private Set<Field> determineMissingFields(
            HttpSession session,
            String storeKey,
            CacheContext context,
            Set<Field> requestedFields) {

        Set<Field> fieldsToFetch =
                EnumSet.noneOf(Field.class);

        for (Field field : requestedFields) {

            // NOMINEE has special logic.
            if (field == Field.NOMINEE) {
                continue;
            }

            if (context.cacheReadFailed
                    || !context.localCache.containsKey(field)) {

                fieldsToFetch.add(field);
            }
        }

        // --------------------------------------------------------
        // SPECIAL POLICY + NOMINEE HANDLING
        // --------------------------------------------------------

        if (POLICY_STORE_KEY.equals(storeKey)
                && requestedFields.contains(Field.NOMINEE)) {

            determineNomineeFields(
                    session,
                    context,
                    fieldsToFetch);
        }

        // --------------------------------------------------------
        // CLIENT FETCH + NOMINEE
        // --------------------------------------------------------

        if (CLIENT_STORE_KEY.equals(storeKey)
                && requestedFields.contains(Field.NOMINEE)) {

            /*
             * Client Fetch returns the nominee list directly.
             * If NOMINEE isn't available for this request,
             * ask the backend for it.
             */
            if (context.cacheReadFailed
                    || !context.localCache.containsKey(Field.NOMINEE)) {

                fieldsToFetch.add(Field.NOMINEE);
            }
        }

        return fieldsToFetch;
    }

    // ============================================================
    // 3. POLICY NOMINEE LOGIC
    // ============================================================

    private void determineNomineeFields(
            HttpSession session,
            CacheContext context,
            Set<Field> fieldsToFetch) {

        boolean roleMissing =
                context.cacheReadFailed
                        || !context.localCache.containsKey(Field.ROLE);

        if (roleMissing) {

            /*
             * We need ROLE to know which client IDs
             * are nominees.
             *
             * Therefore one API call:
             * ROLE + NOMINEE
             */
            fieldsToFetch.add(Field.ROLE);
            fieldsToFetch.add(Field.NOMINEE);

            return;
        }

        /*
         * ROLE already exists.
         *
         * Find nominee client IDs from ROLE.
         */
        List<String> nomineeClientIds =
                getNomineeClientIds(
                        context.localCache.get(Field.ROLE));

        /*
         * Check whether every nominee is already present
         * in CLIENT_STORE.
         */
        boolean nomineeMissing =
                isAnyNomineeMissing(
                        session,
                        nomineeClientIds);

        if (nomineeMissing) {

            /*
             * Fetch complete nominee list once.
             *
             * We do not make one API call per nominee.
             */
            fieldsToFetch.add(Field.NOMINEE);
        }
    }

    // ============================================================
    // 4. GET NOMINEE CLIENT IDS FROM ROLE
    // ============================================================

    private List<String> getNomineeClientIds(
            List<?> roleData) {

        Set<String> clientIds =
                new LinkedHashSet<>();

        if (roleData == null) {
            return new ArrayList<>();
        }

        for (Object value : roleData) {

            if (!(value instanceof RoleDto role)) {
                continue;
            }

            if (!isNomineeRole(role)) {
                continue;
            }

            String clientId =
                    role.getClientId();

            if (clientId != null
                    && !clientId.isBlank()) {

                clientIds.add(clientId);
            }
        }

        return new ArrayList<>(clientIds);
    }

    // ============================================================
    // 5. CHECK CLIENT STORE
    // ============================================================

    @SuppressWarnings("unchecked")
    private boolean isAnyNomineeMissing(
            HttpSession session,
            List<String> nomineeClientIds) {

        if (nomineeClientIds.isEmpty()) {
            return false;
        }

        Map<String, Map<Field, List<?>>> clientStore =
                getStore(session, CLIENT_STORE_KEY);

        for (String clientId : nomineeClientIds) {

            Map<Field, List<?>> clientEntry =
                    clientStore.get(clientId);

            if (clientEntry == null) {
                return true;
            }

            if (!clientEntry.containsKey(Field.CLIENT_INFO)) {
                return true;
            }
        }

        return false;
    }

    // ============================================================
    // 6. CALL BACKEND API
    // ============================================================

    private FetchResponseDTO callBackendApi(
            String storeKey,
            String id,
            Set<Field> fieldsToFetch) {

        List<String> apiFields =
                fieldsToFetch.stream()
                        .map(Field::getApiFieldName)
                        .filter(Objects::nonNull)
                        .toList();

        if (apiFields.isEmpty()) {
            return null;
        }

        try {

            log.info(
                    "Calling backend API. store={}, id={}, fields={}",
                    storeKey,
                    id,
                    apiFields);

            if (CLIENT_STORE_KEY.equals(storeKey)) {

                return backendApiClient.fetchClientData(
                        id,
                        apiFields);
            }

            if (POLICY_STORE_KEY.equals(storeKey)) {

                return backendApiClient.fetchPolicyData(
                        id,
                        apiFields);
            }

        } catch (Exception ex) {

            log.error(
                    "Backend API failed. store={}, id={}. "
                            + "Using available cache.",
                    storeKey,
                    id,
                    ex);
        }

        return null;
    }

    // ============================================================
    // 7. UPDATE CACHE
    // ============================================================

    private void updateCache(
            HttpSession session,
            String storeKey,
            String id,
            CacheContext context,
            Set<Field> fieldsFetched,
            FetchResponseDTO apiResponse) {

        for (Field field : fieldsFetched) {

            List<?> freshData =
                    field.extractFrom(apiResponse);

            if (freshData == null) {
                continue;
            }

            if (field == Field.NOMINEE) {

                /*
                 * IMPORTANT:
                 *
                 * NOMINEE is NOT stored under P1/P2.
                 *
                 * Each nominee becomes:
                 *
                 * CLIENT_STORE[C1][CLIENT_INFO]
                 */
                storeNominees(
                        session,
                        freshData);

            } else {

                /*
                 * Normal field:
                 *
                 * POLICY_STORE[P1][ROLE]
                 * POLICY_STORE[P1][POLICY]
                 * CLIENT_STORE[C1][NEFT]
                 * etc.
                 */
                context.localCache.put(
                        field,
                        freshData);
            }
        }

        /*
         * Save normal fields back to current store.
         */
        saveCurrentStore(
                session,
                storeKey,
                id,
                context);
    }

    // ============================================================
    // 8. STORE NOMINEES IN CLIENT_STORE
    // ============================================================

    private void storeNominees(
            HttpSession session,
            List<?> nomineeData) {

        if (session == null || nomineeData == null) {
            return;
        }

        Map<String, Map<Field, List<?>>> clientStore =
                getStore(
                        session,
                        CLIENT_STORE_KEY);

        for (Object value : nomineeData) {

            if (!(value instanceof NomineeDto nominee)) {
                continue;
            }

            String clientId =
                    nominee.getClientId();

            if (clientId == null
                    || clientId.isBlank()) {

                continue;
            }

            /*
             * One client ID = one client cache entry.
             */
            Map<Field, List<?>> clientEntry =
                    clientStore.computeIfAbsent(
                            clientId,
                            key -> new EnumMap<>(Field.class));

            /*
             * Store nominee data as CLIENT_INFO.
             *
             * Existing fields such as NEFT can live beside it.
             */
            clientEntry.put(
                    Field.CLIENT_INFO,
                    List.of(nominee));
        }

        session.setAttribute(
                CLIENT_STORE_KEY,
                clientStore);
    }

    // ============================================================
    // 9. SAVE CURRENT CLIENT/POLICY STORE
    // ============================================================

    private void saveCurrentStore(
            HttpSession session,
            String storeKey,
            String id,
            CacheContext context) {

        if (session == null) {
            return;
        }

        try {

            Map<String, Map<Field, List<?>>> masterStore =
                    context.masterStore;

            if (masterStore == null) {
                masterStore = new HashMap<>();
            }

            masterStore.put(
                    id,
                    context.localCache);

            session.setAttribute(
                    storeKey,
                    masterStore);

        } catch (Exception ex) {

            log.error(
                    "Cache write failed. store={}, id={}",
                    storeKey,
                    id,
                    ex);
        }
    }

    // ============================================================
    // 10. BUILD FINAL RESPONSE
    // ============================================================

    private FetchResponseDTO buildResponse(
            HttpSession session,
            String storeKey,
            CacheContext context,
            Set<Field> requestedFields) {

        FetchResponseDTO response =
                new FetchResponseDTO();

        for (Field field : requestedFields) {

            if (field == Field.NOMINEE) {
                continue;
            }

            List<?> data =
                    context.localCache.get(field);

            if (data != null) {

                field.populateInto(
                        response,
                        data);
            }
        }

        /*
         * Policy nominee response is rebuilt from:
         *
         * POLICY_STORE[P1][ROLE]
         *          ↓
         * nominee client IDs
         *          ↓
         * CLIENT_STORE[clientId][CLIENT_INFO]
         */
        if (POLICY_STORE_KEY.equals(storeKey)
                && requestedFields.contains(Field.NOMINEE)) {

            List<NomineeDto> nominees =
                    buildNomineesFromClientStore(
                            session,
                            context.localCache.get(Field.ROLE));

            response.setNomineeList(nominees);
        }

        /*
         * For Client Fetch, NOMINEE is already a normal API/cache
         * field because Client Fetch itself returns nomineeList.
         */
        if (CLIENT_STORE_KEY.equals(storeKey)
                && requestedFields.contains(Field.NOMINEE)) {

            List<?> nomineeData =
                    context.localCache.get(Field.NOMINEE);

            if (nomineeData != null) {

                Field.NOMINEE.populateInto(
                        response,
                        nomineeData);
            }
        }

        return response;
    }

    // ============================================================
    // 11. BUILD NOMINEE RESPONSE FROM CLIENT_STORE
    // ============================================================

    private List<NomineeDto> buildNomineesFromClientStore(
            HttpSession session,
            List<?> roleData) {

        List<NomineeDto> nominees =
                new ArrayList<>();

        List<String> nomineeClientIds =
                getNomineeClientIds(roleData);

        Map<String, Map<Field, List<?>>> clientStore =
                getStore(
                        session,
                        CLIENT_STORE_KEY);

        for (String clientId : nomineeClientIds) {

            Map<Field, List<?>> clientEntry =
                    clientStore.get(clientId);

            if (clientEntry == null) {
                continue;
            }

            List<?> clientInfo =
                    clientEntry.get(Field.CLIENT_INFO);

            if (clientInfo == null
                    || clientInfo.isEmpty()) {
                continue;
            }

            Object value =
                    clientInfo.get(0);

            if (value instanceof NomineeDto nominee) {
                nominees.add(nominee);
            }
        }

        return nominees;
    }

    // ============================================================
    // 12. ROLE CHECK
    // ============================================================

    private boolean isNomineeRole(RoleDto role) {

        if (role == null || role.getRole() == null) {
            return false;
        }

        String roleName =
                role.getRole().trim();

        return "NOMINEE".equalsIgnoreCase(roleName)
                || "BEN".equalsIgnoreCase(roleName)
                || "BENEFICIARY".equalsIgnoreCase(roleName);
    }

    // ============================================================
    // 13. READ A STORE
    // ============================================================

    @SuppressWarnings("unchecked")
    private Map<String, Map<Field, List<?>>> getStore(
            HttpSession session,
            String storeKey) {

        if (session == null) {
            return new HashMap<>();
        }

        Map<String, Map<Field, List<?>>> store =
                (Map<String, Map<Field, List<?>>>) session
                        .getAttribute(storeKey);

        if (store == null) {
            store = new HashMap<>();
        }

        return store;
    }

    // ============================================================
    // CACHE CONTEXT
    // ============================================================

    private static class CacheContext {

        private Map<String, Map<Field, List<?>>> masterStore;

        private Map<Field, List<?>> localCache =
                new EnumMap<>(Field.class);

        private boolean cacheReadFailed;
    }
}
