package splitwise.splitwise.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import splitwise.splitwise.dto.AddExpenseRequest;
import splitwise.splitwise.dto.ExpenseSplitResponse;
import splitwise.splitwise.enums.SplitType;
import splitwise.splitwise.exception.ExpenseNotFound;
import splitwise.splitwise.exception.GroupNotFound;
import splitwise.splitwise.exception.UserNotFound;
import splitwise.splitwise.model.*;
import splitwise.splitwise.repository.*;
import splitwise.splitwise.strategy.SplitStrategy;
import splitwise.splitwise.strategy.SplitStrategyFactory;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpenseServiceTest {
    @Mock ExpenseRepository expenseRepository;
    @Mock UserRepository userRepository;
    @Mock ExpenseGroupRepository groupRepository;
    @Mock ExpenseSplitRepository splitRepository;
    @Mock SettlementRepository settlementRepository;
    @Mock SplitStrategyFactory splitStrategyFactory;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock SplitStrategy splitStrategy;

    @InjectMocks ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private ExpenseGroup mockGroup(Long id) {
        ExpenseGroup group = new ExpenseGroup();
        group.setGroupid(id);
        return group;
    }
    private User mockUser(Long id, String name) {
        User user = new User();
        user.setUserid(id);
        user.setUsername(name);
        return user;
    }
    private Expense mockExpense(Long id, ExpenseGroup group, User user) {
        Expense expense = new Expense();
        expense.setExpenseid(id);
        expense.setGroupid(group);
        expense.setUserid(user);
        expense.setAmount(100.0);
        expense.setDescription("desc");
        expense.setExpensedate(new Timestamp(System.currentTimeMillis()));
        return expense;
    }

    @Test
    void addExpense_success() {
        Long groupId = 1L;
        Long userId = 2L;
        AddExpenseRequest req = new AddExpenseRequest();
        req.setPaidBy(userId);
        req.setAmount(100.0);
        req.setDescription("desc");
        req.setParticipants(List.of(userId));
        req.setSplitType(SplitType.EQUAL);
        req.setValues(List.of(100.0));

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup(groupId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser(userId, "Rahul")));
        when(groupMemberRepository.existsById_GroupidAndId_Userid(groupId, userId)).thenReturn(true);
        when(expenseRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(splitStrategyFactory.getStrategy(any(SplitType.class))).thenReturn(splitStrategy);
        when(splitStrategy.calculateSplits(any(), any(), any(), any())).thenReturn(List.of(new ExpenseSplit()));
        when(splitRepository.saveAll(any())).thenReturn(List.of(new ExpenseSplit()));

        Expense result = expenseService.addExpense(groupId, req);
        assertEquals(100.0, result.getAmount());
    }

    @Test
    void addExpense_groupNotFound() {
        when(groupRepository.findById(anyLong())).thenReturn(Optional.empty());
        AddExpenseRequest req = new AddExpenseRequest();
        req.setPaidBy(1L);
        assertThrows(GroupNotFound.class, () -> expenseService.addExpense(1L, req));
    }

    @Test
    void addExpense_userNotFound() {
        when(groupRepository.findById(anyLong())).thenReturn(Optional.of(mockGroup(1L)));
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
        AddExpenseRequest req = new AddExpenseRequest();
        req.setPaidBy(1L);
        assertThrows(UserNotFound.class, () -> expenseService.addExpense(1L, req));
    }

    @Test
    void addExpense_participantNotGroupMember() {
        Long groupId = 1L;
        Long userId = 2L;
        AddExpenseRequest req = new AddExpenseRequest();
        req.setPaidBy(userId);
        req.setParticipants(List.of(userId));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup(groupId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser(userId, "Rahul")));
        when(groupMemberRepository.existsById_GroupidAndId_Userid(groupId, userId)).thenReturn(false);
        assertThrows(UserNotFound.class, () -> expenseService.addExpense(groupId, req));
    }

    @Test
    void getExpensesByGroup_success() {
        Long groupId = 1L;
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup(groupId)));
        when(expenseRepository.findByGroupid_Groupid(groupId)).thenReturn(List.of(mockExpense(1L, mockGroup(groupId), mockUser(2L, "Rahul"))));
        List<Expense> result = expenseService.getExpensesByGroup(groupId);
        assertFalse(result.isEmpty());
    }

    @Test
    void getExpensesByGroup_groupNotFound() {
        when(groupRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(GroupNotFound.class, () -> expenseService.getExpensesByGroup(1L));
    }

    @Test
    void getExpensesByGroupAndUser_success() {
        Long groupId = 1L, userId = 2L;
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(mockGroup(groupId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser(userId, "Rahul")));
        when(expenseRepository.findByGroupid_GroupidAndUserid_Userid(groupId, userId)).thenReturn(List.of(mockExpense(1L, mockGroup(groupId), mockUser(userId, "Rahul"))));
        List<Expense> result = expenseService.getExpensesByGroupAndUser(groupId, userId);
        assertFalse(result.isEmpty());
    }

    @Test
    void getExpensesByGroupAndUser_groupNotFound() {
        when(groupRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(GroupNotFound.class, () -> expenseService.getExpensesByGroupAndUser(1L, 2L));
    }

    @Test
    void getExpensesByGroupAndUser_userNotFound() {
        when(groupRepository.findById(anyLong())).thenReturn(Optional.of(mockGroup(1L)));
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(UserNotFound.class, () -> expenseService.getExpensesByGroupAndUser(1L, 2L));
    }

    @Test
    void getBalances_success() {
        Long groupId = 1L;
        Expense expense = mockExpense(1L, mockGroup(groupId), mockUser(2L, "Rahul"));
        when(expenseRepository.findByGroupid_Groupid(groupId)).thenReturn(List.of(expense));
        when(settlementRepository.findByGroupid_Groupid(groupId)).thenReturn(List.of());
        when(splitRepository.findByExpenseid(expense.getExpenseid())).thenReturn(List.of(new ExpenseSplit(expense.getExpenseid(), 3L, 100.0)));
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(mockUser(2L, "Rahul"))).thenReturn(Optional.of(mockUser(3L, "Parth")));
        List<String> balances = expenseService.getBalances(groupId);
        assertFalse(balances.isEmpty());
    }

    @Test
    void getUserBalance_success() {
        Long groupId = 1L, userId = 2L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser(userId, "Rahul")));
        when(expenseRepository.findByGroupid_Groupid(groupId)).thenReturn(List.of());
        when(settlementRepository.findByGroupid_Groupid(groupId)).thenReturn(List.of());
        List<String> result = expenseService.getUserBalance(groupId, userId);
        assertNotNull(result);
    }

    @Test
    void getUserBalance_userNotFound() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(UserNotFound.class, () -> expenseService.getUserBalance(1L, 2L));
    }

    @Test
    void getBalanceBetweenUsers_success() {
        Long groupId = 1L, user1Id = 2L, user2Id = 3L;
        when(userRepository.findById(user1Id)).thenReturn(Optional.of(mockUser(user1Id, "Rahul")));
        when(userRepository.findById(user2Id)).thenReturn(Optional.of(mockUser(user2Id, "Parth")));
        when(expenseRepository.findByGroupid_Groupid(groupId)).thenReturn(List.of());
        when(settlementRepository.findByGroupid_Groupid(groupId)).thenReturn(List.of());
        double result = expenseService.getBalanceBetweenUsers(groupId, user1Id, user2Id);
        assertEquals(0.0, result);
    }

    @Test
    void deleteExpense_success() {
        Long groupId = 1L, expenseId = 2L;
        Expense expense = mockExpense(expenseId, mockGroup(groupId), mockUser(3L, "Rahul"));
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));
        doNothing().when(splitRepository).deleteByExpenseid(expenseId);
        doNothing().when(expenseRepository).delete(expense);
        assertDoesNotThrow(() -> expenseService.deleteExpense(groupId, expenseId));
    }

    @Test
    void deleteExpense_notFound() {
        when(expenseRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(ExpenseNotFound.class, () -> expenseService.deleteExpense(1L, 2L));
    }

    @Test
    void updateExpense_success() {
        Long expenseId = 1L;
        Expense expense = mockExpense(expenseId, mockGroup(1L), mockUser(2L, "Rahul"));
        AddExpenseRequest req = new AddExpenseRequest();
        req.setPaidBy(2L);
        req.setParticipants(List.of(2L));
        req.setAmount(200.0);
        req.setDescription("new");
        req.setSplitType(SplitType.EQUAL);
        req.setValues(List.of(200.0));

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));
        when(userRepository.findById(2L)).thenReturn(Optional.of(mockUser(2L, "Rahul")));
        when(groupMemberRepository.existsById_GroupidAndId_Userid(anyLong(), anyLong())).thenReturn(true);
        doNothing().when(splitRepository).deleteByExpenseid(expenseId);
        when(expenseRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(splitStrategyFactory.getStrategy(any(SplitType.class))).thenReturn(splitStrategy);
        when(splitStrategy.calculateSplits(any(), any(), any(), any())).thenReturn(List.of(new ExpenseSplit()));
        when(splitRepository.saveAll(any())).thenReturn(List.of(new ExpenseSplit()));

        Expense result = expenseService.updateExpense(expenseId, req);
        assertEquals(200.0, result.getAmount());
    }

    @Test
    void updateExpense_notFound() {
        when(expenseRepository.findById(anyLong())).thenReturn(Optional.empty());
        AddExpenseRequest req = new AddExpenseRequest();
        assertThrows(ExpenseNotFound.class, () -> expenseService.updateExpense(1L, req));
    }

    @Test
    void getExpenseByGroupAndId_success() {
        Long groupId = 1L, expenseId = 2L;
        Expense expense = mockExpense(expenseId, mockGroup(groupId), mockUser(3L, "Rahul"));
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));
        Expense result = expenseService.getExpenseByGroupAndId(groupId, expenseId);
        assertEquals(expenseId, result.getExpenseid());
    }

    @Test
    void getExpenseByGroupAndId_notFound() {
        when(expenseRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(ExpenseNotFound.class, () -> expenseService.getExpenseByGroupAndId(1L, 2L));
    }

    @Test
    void getExpenseByGroupAndId_wrongGroup() {
        Expense expense = mockExpense(2L, mockGroup(99L), mockUser(3L, "Rahul"));
        when(expenseRepository.findById(2L)).thenReturn(Optional.of(expense));
        assertThrows(GroupNotFound.class, () -> expenseService.getExpenseByGroupAndId(1L, 2L));
    }

    @Test
    void getExpenseSplitsByGroupAndExpenseId_success() {
        Long groupId = 1L, expenseId = 2L;
        Expense expense = mockExpense(expenseId, mockGroup(groupId), mockUser(3L, "Rahul"));
        ExpenseSplit split = new ExpenseSplit(expenseId, 3L, 100.0);
        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(expense));
        when(splitRepository.findByExpenseid(expenseId)).thenReturn(List.of(split));
        when(userRepository.findById(3L)).thenReturn(Optional.of(mockUser(3L, "Rahul")));
        List<ExpenseSplitResponse> result = expenseService.getExpenseSplitsByGroupAndExpenseId(groupId, expenseId);
        assertFalse(result.isEmpty());
    }

    @Test
    void getExpenseSplitsByGroupAndExpenseId_notFound() {
        when(expenseRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(ExpenseNotFound.class, () -> expenseService.getExpenseSplitsByGroupAndExpenseId(1L, 2L));
    }

    @Test
    void getExpenseSplitsByGroupAndExpenseId_wrongGroup() {
        Expense expense = mockExpense(2L, mockGroup(99L), mockUser(3L, "Rahul"));
        when(expenseRepository.findById(2L)).thenReturn(Optional.of(expense));
        assertThrows(GroupNotFound.class, () -> expenseService.getExpenseSplitsByGroupAndExpenseId(1L, 2L));
    }
}

