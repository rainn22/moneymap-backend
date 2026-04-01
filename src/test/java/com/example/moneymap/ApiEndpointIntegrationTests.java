package com.example.moneymap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.moneymap.features.alert.repository.AlertRepository;
import com.example.moneymap.features.auth.entity.VerificationToken;
import com.example.moneymap.features.auth.repository.RefreshTokenRepository;
import com.example.moneymap.features.auth.repository.RevokedTokenRepository;
import com.example.moneymap.features.auth.repository.VerificationTokenRepository;
import com.example.moneymap.features.budget.repository.BudgetRepository;
import com.example.moneymap.features.category.entity.Category;
import com.example.moneymap.features.category.repository.CategoryRepository;
import com.example.moneymap.features.saving.repository.SavingGoalRepository;
import com.example.moneymap.features.transaction.entity.TransactionType;
import com.example.moneymap.features.transaction.repository.TransactionRepository;
import com.example.moneymap.features.user.entity.Role;
import com.example.moneymap.features.user.entity.User;
import com.example.moneymap.features.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "app.jwt.secret=1234567890123456789012345678901234567890123456789012345678901234",
        "app.jwt.expiration=86400000",
        "app.jwt.refreshExpiration=604800000",
        "app.mail.enabled=false",
        "app.admin.bootstrap.enabled=false"
})
@AutoConfigureMockMvc
class ApiEndpointIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private SavingGoalRepository savingGoalRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        transactionRepository.deleteAll();
        budgetRepository.deleteAll();
        savingGoalRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        revokedTokenRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void authEndpoints_shouldSupportRegisterVerifyLoginRefreshAndLogout() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo_user",
                                  "email": "demo_user@example.com",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        VerificationToken verificationToken = verificationTokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/auth/verify")
                        .param("token", verificationToken.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "demo_user@example.com",
                                  "password": "Password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andReturn();

        String refreshToken = readPath(loginResult, "data", "refreshToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void userEndpoints_shouldCoverBudgetTransactionAlertDashboardAndSavingGoalFlows() throws Exception {
        seedVerifiedUser("demo_user", "demo_user@example.com", "Password123", Role.USER);
        Category foodCategory = seedCategory("Food", TransactionType.EXPENSE);

        String userToken = loginAndGetAccessToken("demo_user@example.com", "Password123");

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo_user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(patch("/api/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Demo",
                                  "lastName": "User"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Demo"))
                .andExpect(jsonPath("$.lastName").value("User"));

        mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "periodType": "MONTHLY",
                                  "amountLimit": 300,
                                  "startDate": "2026-04-01",
                                  "endDate": "2026-04-30"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categoryName").value("Food"));

        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryName").value("Food"));

        MvcResult createTransactionResult = mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 260,
                                  "type": "EXPENSE",
                                  "description": "Food expense for alert test",
                                  "transactionDate": "2026-04-20"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categoryName").value("Food"))
                .andReturn();

        String transactionId = readPath(createTransactionResult, "data", "id");

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].description").value("Food expense for alert test"));

        mockMvc.perform(get("/api/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(Long.parseLong(transactionId)));

        MvcResult alertsResult = mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].thresholdPercent").value(80))
                .andReturn();

        String alertId = readPath(alertsResult, "data", "0", "id");

        mockMvc.perform(patch("/api/alerts/{id}/read", alertId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));

        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalExpense").value(260))
                .andExpect(jsonPath("$.data.remainingMonthlyBudget").value(40))
                .andExpect(jsonPath("$.data.unreadAlertsCount").value(0))
                .andExpect(jsonPath("$.data.lastFiveTransactions[0].description").value("Food expense for alert test"));

        MvcResult savingGoalResult = mockMvc.perform(post("/api/saving-goals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Vacation Fund",
                                  "targetAmount": 1200,
                                  "currentAmount": 300,
                                  "deadline": "2026-12-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String savingGoalId = readPath(savingGoalResult, "data", "id");

        mockMvc.perform(get("/api/saving-goals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Vacation Fund"));

        mockMvc.perform(patch("/api/saving-goals/{id}/add-money", savingGoalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 100
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAmount").value(400));

        mockMvc.perform(delete("/api/saving-goals/{id}", savingGoalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void adminEndpoints_shouldCoverCategoryManagementAnalyticsAndMonitoring() throws Exception {
        User admin = seedVerifiedUser("admin", "admin@moneymap.com", "Admin@123456", Role.ADMIN);
        User normalUser = seedVerifiedUser("jane", "jane@example.com", "Password123", Role.USER);
        Category foodCategory = seedCategory("Food", TransactionType.EXPENSE);

        budgetRepository.save(com.example.moneymap.features.budget.entity.Budget.builder()
                .user(normalUser)
                .category(foodCategory)
                .periodType(com.example.moneymap.features.budget.entity.BudgetPeriodType.MONTHLY)
                .amountLimit(new java.math.BigDecimal("300.00"))
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2026, 4, 30))
                .build());

        String userToken = loginAndGetAccessToken("jane@example.com", "Password123");
        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 260,
                                  "type": "EXPENSE",
                                  "description": "Admin-visible transaction",
                                  "transactionDate": "2026-04-20"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk());

        String adminToken = loginAndGetAccessToken("admin@moneymap.com", "Admin@123456");

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Transport",
                                  "type": "EXPENSE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bills",
                                  "type": "EXPENSE"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(2))
                .andExpect(jsonPath("$.data.totalTransactions").value(1))
                .andExpect(jsonPath("$.data.totalBudgets").value(1))
                .andExpect(jsonPath("$.data.totalAlerts").value(1))
                .andExpect(jsonPath("$.data.mostSpentCategory").value("Food"));

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/admin/users/{id}", normalUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("jane@example.com"));

        mockMvc.perform(patch("/api/admin/users/{id}/deactivate", normalUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(get("/api/admin/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].description").value("Admin-visible transaction"));

        mockMvc.perform(get("/api/admin/transactions")
                        .param("userId", normalUser.getId().toString())
                        .param("categoryId", foodCategory.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/admin/alerts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userEmail").value("jane@example.com"));

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingBudgetAfterAlert_shouldAlsoDeleteLinkedAlerts() throws Exception {
        seedVerifiedUser("demo_user", "demo_user@example.com", "Password123", Role.USER);
        Category foodCategory = seedCategory("Food", TransactionType.EXPENSE);
        String userToken = loginAndGetAccessToken("demo_user@example.com", "Password123");

        MvcResult budgetResult = mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "periodType": "MONTHLY",
                                  "amountLimit": 300,
                                  "startDate": "2026-04-01",
                                  "endDate": "2026-04-30"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk())
                .andReturn();

        String budgetId = readPath(budgetResult, "data", "id");

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 260,
                                  "type": "EXPENSE",
                                  "description": "Triggers alert",
                                  "transactionDate": "2026-04-20"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/budgets/{id}", budgetId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private User seedVerifiedUser(String username, String email, String rawPassword, Role role) {
        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .enabled(true)
                .role(role)
                .build());
    }

    private Category seedCategory(String name, TransactionType type) {
        return categoryRepository.save(Category.builder()
                .name(name)
                .type(type)
                .build());
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return readPath(result, "data", "accessToken");
    }

    private String readPath(MvcResult result, String... path) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (String part : path) {
            if (node.isArray() && part.chars().allMatch(Character::isDigit)) {
                node = node.path(Integer.parseInt(part));
            } else {
                node = node.path(part);
            }
        }
        return node.asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
