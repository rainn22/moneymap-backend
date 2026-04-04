package com.example.moneymap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.example.moneymap.features.category.entity.CategoryGroupType;
import com.example.moneymap.features.category.entity.CategorySpendingType;
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
        User user = seedVerifiedUser("demo_user", "demo_user@example.com", "Password123", Role.USER);
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

        MvcResult userCategoryResult = mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Side Hustle",
                                  "type": "INCOME"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Side Hustle"))
                .andExpect(jsonPath("$.data.isDefault").value(false))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andReturn();

        String userCategoryId = readPath(userCategoryResult, "data", "id");

        mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %s,
                                  "amount": 100,
                                  "type": "INCOME",
                                  "description": "Freelance payment",
                                  "transactionDate": "2026-04-10"
                                }
                                """.formatted(userCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("Side Hustle"));

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
                .andExpect(jsonPath("$.data.items[0].description").value("Food expense for alert test"))
                .andExpect(jsonPath("$.data.offset").value(0))
                .andExpect(jsonPath("$.data.limit").value(20))
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(Long.parseLong(transactionId)));

        mockMvc.perform(put("/api/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 260,
                                  "type": "EXPENSE",
                                  "description": "Food expense updated",
                                  "transactionDate": "2026-04-21"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(Long.parseLong(transactionId)))
                .andExpect(jsonPath("$.data.amount").value(260))
                .andExpect(jsonPath("$.data.description").value("Food expense updated"))
                .andExpect(jsonPath("$.data.transactionDate").value("2026-04-21"));

        MvcResult alertsResult = mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].thresholdPercent").value(100))
                .andExpect(jsonPath("$.data[0].alertType").value("DAILY"))
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
                .andExpect(jsonPath("$.data.unreadAlertsCount").value(5))
                .andExpect(jsonPath("$.data.lastFiveTransactions[0].description").value("Food expense updated"));

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
                                  "amount": 100,
                                  "description": "Added to vacation savings",
                                  "transactionDate": "2026-04-22"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAmount").value(400));

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("SAVING"))
                .andExpect(jsonPath("$.data.items[0].savingGoalId").value(Long.parseLong(savingGoalId)))
                .andExpect(jsonPath("$.data.items[0].savingGoalTitle").value("Vacation Fund"))
                .andExpect(jsonPath("$.data.items[0].categoryId").isEmpty())
                .andExpect(jsonPath("$.data.items[0].categoryName").isEmpty())
                .andExpect(jsonPath("$.data.items[0].amount").value(100))
                .andExpect(jsonPath("$.data.items[0].description").value("Added to vacation savings"))
                .andExpect(jsonPath("$.data.items[0].transactionDate").value("2026-04-22"));

        mockMvc.perform(get("/api/transactions")
                        .param("type", "SAVING")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("SAVING"))
                .andExpect(jsonPath("$.data.items[0].savingGoalId").value(Long.parseLong(savingGoalId)));

        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalExpense").value(260))
                .andExpect(jsonPath("$.data.lastFiveTransactions[0].type").value("SAVING"))
                .andExpect(jsonPath("$.data.lastFiveTransactions[0].savingGoalId").value(Long.parseLong(savingGoalId)));

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
    void budgetSetup_shouldSupportManualCategoryAndSavingsAllocations() throws Exception {
        seedVerifiedUser("demo_user", "demo_user@example.com", "Password123", Role.USER);
        Category rentCategory = seedCategory("Rent", TransactionType.EXPENSE, CategoryGroupType.NEEDS, CategorySpendingType.FIXED);
        Category foodCategory = seedCategory("Food", TransactionType.EXPENSE, CategoryGroupType.NEEDS, CategorySpendingType.VARIABLE);
        Category funCategory = seedCategory("Fun", TransactionType.EXPENSE, CategoryGroupType.WANTS, CategorySpendingType.VARIABLE);

        String userToken = loginAndGetAccessToken("demo_user@example.com", "Password123");

        MvcResult savingGoalResult = mockMvc.perform(post("/api/saving-goals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Emergency Fund",
                                  "targetAmount": 5000,
                                  "currentAmount": 500,
                                  "deadline": "2026-12-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String savingGoalId = readPath(savingGoalResult, "data", "id");

        mockMvc.perform(post("/api/budgets/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estimatedMonthlyIncome": 1000,
                                  "periodType": "MONTHLY",
                                  "startDate": "2026-04-01",
                                  "endDate": "2026-04-30",
                                  "allocations": [
                                    {
                                      "allocationType": "CATEGORY",
                                      "categoryId": %d,
                                      "amountLimit": 200
                                    },
                                    {
                                      "allocationType": "CATEGORY",
                                      "categoryId": %d,
                                      "percentage": 10
                                    },
                                    {
                                      "allocationType": "SAVINGS",
                                      "savingGoalId": %s,
                                      "percentage": 20
                                    }
                                  ]
                                }
                                """.formatted(rentCategory.getId(), funCategory.getId(), savingGoalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPercentage").value(50))
                .andExpect(jsonPath("$.data.totalAllocatedAmount").value(500))
                .andExpect(jsonPath("$.data.fixedTotal").value(200))
                .andExpect(jsonPath("$.data.savingsTotal").value(200))
                .andExpect(jsonPath("$.data.remainingAmount").value(600))
                .andExpect(jsonPath("$.data.dailyBudget").value(20))
                .andExpect(jsonPath("$.data.budgets[2].savingGoalId").value(Long.parseLong(savingGoalId)))
                .andExpect(jsonPath("$.data.budgets[2].savingGoalTitle").value("Emergency Fund"));

        mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amountLimit": 200,
                                  "periodType": "MONTHLY",
                                  "startDate": "2026-04-01",
                                  "endDate": "2026-04-30"
                                }
                                """.formatted(rentCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("Rent"))
                .andExpect(jsonPath("$.data.dailyRecommendedAmount").isEmpty())
                .andExpect(jsonPath("$.data.weeklyRecommendedAmount").isEmpty());

        mockMvc.perform(post("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "estimatedMonthlyIncome": 1000,
                                  "percentage": 10,
                                  "periodType": "MONTHLY",
                                  "startDate": "2026-04-01",
                                  "endDate": "2026-04-30"
                                }
                                """.formatted(funCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allocationType").value("CATEGORY"))
                .andExpect(jsonPath("$.data.categoryName").value("Fun"))
                .andExpect(jsonPath("$.data.amountLimit").value(100))
                .andExpect(jsonPath("$.data.percentage").value(10))
                .andExpect(jsonPath("$.data.dailyRecommendedAmount").value(3.7))
                .andExpect(jsonPath("$.data.weeklyRecommendedAmount").value(25.9));

        mockMvc.perform(get("/api/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 30,
                                  "type": "EXPENSE",
                                  "description": "Food purchase",
                                  "transactionDate": "2026-04-10"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/alerts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getTransactions_shouldSupportCombinedFiltersAndOffsetPagination() throws Exception {
        seedVerifiedUser("demo_user", "demo_user@example.com", "Password123", Role.USER);
        Category foodCategory = seedCategory("Food", TransactionType.EXPENSE);
        Category salaryCategory = seedCategory("Salary", TransactionType.INCOME);

        String userToken = loginAndGetAccessToken("demo_user@example.com", "Password123");

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 25,
                                  "type": "EXPENSE",
                                  "description": "Coffee beans",
                                  "transactionDate": "2026-04-03"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 40,
                                  "type": "EXPENSE",
                                  "description": "Coffee shop",
                                  "transactionDate": "2026-04-02"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 2000,
                                  "type": "INCOME",
                                  "description": "Monthly salary",
                                  "transactionDate": "2026-04-01"
                                }
                                """.formatted(salaryCategory.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/transactions")
                        .param("search", "coffee")
                        .param("type", "EXPENSE")
                        .param("categoryId", foodCategory.getId().toString())
                        .param("offset", "1")
                        .param("limit", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].description").value("Coffee shop"))
                .andExpect(jsonPath("$.data.offset").value(1))
                .andExpect(jsonPath("$.data.limit").value(1))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void categories_shouldAllowSavingAsCategoryType() throws Exception {
        seedVerifiedUser("demo_user", "demo_user@example.com", "Password123", Role.USER);
        String userToken = loginAndGetAccessToken("demo_user@example.com", "Password123");

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Car Fund",
                                  "type": "SAVING",
                                  "groupType": "SAVING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Car Fund"))
                .andExpect(jsonPath("$.data.type").value("SAVING"))
                .andExpect(jsonPath("$.data.groupType").value("SAVING"));
    }

    @Test
    void updateTransaction_shouldEditOwnedTransaction() throws Exception {
        seedVerifiedUser("demo_user", "demo_user@example.com", "Password123", Role.USER);
        Category foodCategory = seedCategory("Food", TransactionType.EXPENSE);
        Category salaryCategory = seedCategory("Salary", TransactionType.INCOME);

        String userToken = loginAndGetAccessToken("demo_user@example.com", "Password123");

        MvcResult createTransactionResult = mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 25,
                                  "type": "EXPENSE",
                                  "description": "Coffee beans",
                                  "transactionDate": "2026-04-03"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk())
                .andReturn();

        String transactionId = readPath(createTransactionResult, "data", "id");

        mockMvc.perform(put("/api/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 2100,
                                  "type": "INCOME",
                                  "description": "Updated salary",
                                  "transactionDate": "2026-04-10"
                                }
                                """.formatted(salaryCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(Long.parseLong(transactionId)))
                .andExpect(jsonPath("$.data.categoryId").value(salaryCategory.getId()))
                .andExpect(jsonPath("$.data.categoryName").value("Salary"))
                .andExpect(jsonPath("$.data.amount").value(2100))
                .andExpect(jsonPath("$.data.type").value("INCOME"))
                .andExpect(jsonPath("$.data.description").value("Updated salary"))
                .andExpect(jsonPath("$.data.transactionDate").value("2026-04-10"));

        mockMvc.perform(get("/api/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(salaryCategory.getId()))
                .andExpect(jsonPath("$.data.type").value("INCOME"))
                .andExpect(jsonPath("$.data.description").value("Updated salary"));
    }

    @Test
    void adminEndpoints_shouldCoverCategoryManagementAndMonitoring() throws Exception {
        User admin = seedVerifiedUser("admin", "admin@moneymap.com", "Admin@123456", Role.ADMIN);
        User normalUser = seedVerifiedUser("jane", "jane@example.com", "Password123", Role.USER);
        Category foodCategory = seedCategory("Food", TransactionType.EXPENSE);
        Category salaryCategory = seedCategory("Salary", TransactionType.INCOME);

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

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 40,
                                  "type": "EXPENSE",
                                  "description": "Another private transaction",
                                  "transactionDate": "2026-04-21"
                                }
                                """.formatted(foodCategory.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "amount": 1000,
                                  "type": "INCOME",
                                  "description": "Salary should not appear in spending trend",
                                  "transactionDate": "2026-04-22"
                                }
                                """.formatted(salaryCategory.getId())))
                .andExpect(status().isOk());

        String adminToken = loginAndGetAccessToken("admin@moneymap.com", "Admin@123456");

        mockMvc.perform(post("/api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Transport",
                                  "type": "EXPENSE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Transport"))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.userId").isEmpty());

        Category transportCategory = categoryRepository.findDefaultByNameIgnoreCase("Transport")
                .orElseThrow();

        mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Should Fail",
                                  "type": "EXPENSE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Admins must use the admin category endpoint"));

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bills",
                                  "type": "EXPENSE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Bills"))
                .andExpect(jsonPath("$.data.isDefault").value(false));

        mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(2))
                .andExpect(jsonPath("$.data.totalTransactions").value(3))
                .andExpect(jsonPath("$.data.totalBudgets").value(1))
                .andExpect(jsonPath("$.data.totalAlerts").value(11))
                .andExpect(jsonPath("$.data.mostSpentCategory").value("Food"));

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/admin/users/{id}", normalUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("jane@example.com"));

        mockMvc.perform(get("/api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(patch("/api/admin/users/{id}/deactivate", normalUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(patch("/api/admin/users/{id}/reactivate", normalUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(delete("/api/admin/categories/{id}", transportCategory.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

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
        return seedCategory(name, type, null, null);
    }

    private Category seedCategory(String name, TransactionType type, CategoryGroupType groupType) {
        return seedCategory(name, type, groupType, null);
    }

    private Category seedCategory(
            String name,
            TransactionType type,
            CategoryGroupType groupType,
            CategorySpendingType spendingType
    ) {
        return categoryRepository.save(Category.builder()
                .name(name)
                .type(type)
                .groupType(groupType)
                .spendingType(spendingType)
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
