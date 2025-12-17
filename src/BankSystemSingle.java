import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * BankSystemSingle.java
 * Role-based Banking desktop app with Employee & Loan modules (single file)
 *
 * - Roles: Admin (admin123), Employee (employee1 / emp123), User (account number)
 * - Accounts persisted to Bank.dat
 * - Loans persisted to Loans.dat
 * - Change log appended to Bank_changes.log
 *
 * Improvements in this version:
 * - Single persistent employee id (EMP_ID) and password (EMP_PASSWORD)
 *   --> employee-submitted loans remain associated with EMP_ID across sessions
 * - Loan status updates (Approve / Reject) persist to Loans.dat
 */
public class BankSystemSingle {
    private static final String DATA_FILE = "Bank.dat";
    private static final String LOG_FILE = "Bank_changes.log";
    private static final String LOAN_FILE = "Loans.dat";

    // Admin credentials (hard-coded default)
    private static final String ADMIN_PASSWORD = "admin123";

    // Single persistent employee credentials (requested)
    private static final String EMP_ID = "employee1";
    private static final String EMP_PASSWORD = "emp123";

    private ArrayList<Account> accounts;
    private ArrayList<String> changeLog;
    private ArrayList<Loan> loans;

    private JFrame frame;
    private JTable table;
    private DefaultTableModel tableModel;

    // login state
    private boolean isAdmin = false;
    private boolean isEmployee = false;
    private String currentUserAccNo = null; // when user logs in, their account number

    public BankSystemSingle() {
        accounts = loadAccounts();
        changeLog = loadChangeLog();
        loans = loadLoans();

        boolean ok = showLoginDialog();
        if (!ok) {
            System.exit(0);
        }

        initUI();
    }

    // ---------------- Login ----------------
    private boolean showLoginDialog() {
        Object[] options = {"Admin", "Employee", "User", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                null,
                "Select role to login:",
                "Login",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[3]
        );

        if (choice == JOptionPane.CLOSED_OPTION || choice == 3) return false;

        if (choice == 0) { // Admin
            JPanel ap = new JPanel(new BorderLayout(6,6));
            ap.add(new JLabel("Enter admin password:"), BorderLayout.NORTH);
            JPasswordField pf = new JPasswordField();
            ap.add(pf, BorderLayout.CENTER);
            int r = JOptionPane.showConfirmDialog(null, ap, "Admin Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return false;
            String pw = new String(pf.getPassword());
            if (!ADMIN_PASSWORD.equals(pw)) {
                JOptionPane.showMessageDialog(null, "Incorrect password. Exiting.");
                return false;
            }
            isAdmin = true;
            isEmployee = false;
            return true;
        } else if (choice == 1) { // Employee
            JPanel ep = new JPanel(new BorderLayout(6,6));
            ep.add(new JLabel("Enter employee id and password:"), BorderLayout.NORTH);
            JPanel creds = new JPanel(new GridLayout(2,2,6,6));
            creds.add(new JLabel("Employee ID:"));
            JTextField idField = new JTextField(EMP_ID);
            creds.add(idField);
            creds.add(new JLabel("Password:"));
            JPasswordField pf = new JPasswordField();
            creds.add(pf);
            ep.add(creds, BorderLayout.CENTER);
            int r = JOptionPane.showConfirmDialog(null, ep, "Employee Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return false;
            String id = idField.getText().trim();
            String pw = new String(pf.getPassword());
            // Validate against single persistent employee credentials
            if (!EMP_ID.equals(id) || !EMP_PASSWORD.equals(pw)) {
                JOptionPane.showMessageDialog(null, "Incorrect employee ID or password. Exiting.");
                return false;
            }
            isEmployee = true;
            isAdmin = false;
            // use persistent EMP_ID
            JOptionPane.showMessageDialog(null, "Logged in as employee: " + EMP_ID);
            return true;
        } else { // User
            String accNo = JOptionPane.showInputDialog(null, "Enter your Account Number to login (User):");
            if (accNo == null || accNo.trim().isEmpty()) return false;
            accNo = accNo.trim();
            Optional<Account> opt = findAccount(accNo);
            if (opt.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Account not found. Please ask admin or employee to create your account first.");
                return false;
            }
            isAdmin = false;
            isEmployee = false;
            currentUserAccNo = accNo;
            return true;
        }
    }

    // ---------------- UI ----------------
    private void initUI() {
        String role = isAdmin ? "Admin" : (isEmployee ? "Employee (" + EMP_ID + ")" : "User: " + currentUserAccNo);
        frame = new JFrame("Banking System - Single File Edition (" + role + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(980, 600);
        frame.setLocationRelativeTo(null);

        JLabel title = new JLabel("Banking System", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Buttons
        JPanel buttons = new JPanel(new GridLayout(2, 6, 8, 8));
        buttons.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton addBtn = new JButton("Add Account (Admin)");
        JButton empAddBtn = new JButton("Add Account (Employee)");
        JButton delBtn = new JButton("Delete Account");
        JButton depBtn = new JButton("Deposit");
        JButton witBtn = new JButton("Withdraw");
        JButton findBtn = new JButton("Find Account");
        JButton viewBtn = new JButton("View All");
        JButton applyLoanBtn = new JButton("Apply Loan");
        JButton myLoansBtn = new JButton("My Loans");
        JButton loanDashboardBtn = new JButton("Loan Dashboard");
        JButton helpBtn = new JButton("Help");
        JButton saveBtn = new JButton("Save Now");
        JButton exitBtn = new JButton("Exit");
        JButton refreshBtn = new JButton("Refresh Table");
        JButton logBtn = new JButton("Data Change Log");

        // add to panel
        buttons.add(addBtn);
        buttons.add(empAddBtn);
        buttons.add(delBtn);
        buttons.add(depBtn);
        buttons.add(witBtn);
        buttons.add(findBtn);
        buttons.add(viewBtn);
        buttons.add(applyLoanBtn);
        buttons.add(myLoansBtn);
        buttons.add(loanDashboardBtn);
        buttons.add(refreshBtn);
        buttons.add(saveBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.CENTER);
        JPanel extra = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        extra.add(logBtn);
        extra.add(helpBtn);
        extra.add(exitBtn);
        south.add(extra, BorderLayout.SOUTH);

        String[] cols = {"Account No", "Name", "Gender", "Mobile", "Account Type", "Balance"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Accounts"));

        frame.setLayout(new BorderLayout(8, 8));
        frame.add(title, BorderLayout.NORTH);
        frame.add(south, BorderLayout.SOUTH);
        frame.add(tableScroll, BorderLayout.CENTER);

        refreshTable();

        // Listeners
        addBtn.addActionListener(e -> {
            if (!isAdmin) {
                JOptionPane.showMessageDialog(frame, "Only admin can add accounts here. Employees should use the Employee add button.");
                return;
            }
            showAddDialog("ADMIN");
        });
        empAddBtn.addActionListener(e -> {
            if (!isEmployee) {
                JOptionPane.showMessageDialog(frame, "Employee add available only to employees.");
                return;
            }
            showAddDialog("EMPLOYEE");
        });
        delBtn.addActionListener(e -> {
            if (!isAdmin) {
                JOptionPane.showMessageDialog(frame, "Only admin can delete accounts.");
                return;
            }
            showDeleteDialog();
        });
        depBtn.addActionListener(e -> showDepositDialog());
        witBtn.addActionListener(e -> showWithdrawDialog());
        findBtn.addActionListener(e -> {
            if (isAdmin) showFindDialog();
            else if (isEmployee) showFindDialog();
            else showFindDialogForUser();
        });
        viewBtn.addActionListener(e -> {
            if (!isAdmin) {
                JOptionPane.showMessageDialog(frame, "Only admin can view all accounts.");
                return;
            }
            showViewAllDialog();
        });
        applyLoanBtn.addActionListener(e -> showApplyLoanDialog());
        myLoansBtn.addActionListener(e -> showMyLoansDialog());
        loanDashboardBtn.addActionListener(e -> {
            if (!isAdmin) {
                JOptionPane.showMessageDialog(frame, "Only admin can access Loan Dashboard.");
                return;
            }
            showLoanDashboard();
        });
        helpBtn.addActionListener(e -> showHelpDialog());
        saveBtn.addActionListener(e -> {
            if (!isAdmin) {
                JOptionPane.showMessageDialog(frame, "Only admin can save entire dataset.");
                return;
            }
            saveAccounts();
            saveLoans();
            JOptionPane.showMessageDialog(frame, "Saved to " + DATA_FILE + " & " + LOAN_FILE);
        });
        exitBtn.addActionListener(e -> {
            saveAccounts();
            saveLoans();
            System.exit(0);
        });
        refreshBtn.addActionListener(e -> refreshTable());
        logBtn.addActionListener(e -> {
            if (!isAdmin) {
                JOptionPane.showMessageDialog(frame, "Only admin can view change log.");
                return;
            }
            showChangeLogDialog();
        });

        // Role-based UI tuning
        if (isAdmin) {
            empAddBtn.setEnabled(false); // admin uses main add
            myLoansBtn.setText("All Loans (Admin view)");
        } else if (isEmployee) {
            addBtn.setEnabled(false);
            delBtn.setEnabled(false);
            viewBtn.setEnabled(false);
            saveBtn.setEnabled(false);
            loanDashboardBtn.setEnabled(false);
            logBtn.setEnabled(false);
            myLoansBtn.setText("Loans Submitted by Employee");
        } else { // user
            addBtn.setEnabled(false);
            empAddBtn.setEnabled(false);
            delBtn.setEnabled(false);
            viewBtn.setEnabled(false);
            saveBtn.setEnabled(false);
            loanDashboardBtn.setEnabled(false);
            logBtn.setEnabled(false);
            findBtn.setText("My Account");
            myLoansBtn.setText("My Loans");
        }

        frame.setVisible(true);
    }

    // ---------------- Account dialogs (ADD generalized to accept creator type) ----------------
    private void showAddDialog(String creatorType) {
        JDialog dialog = new JDialog(frame, "Add New Account", true);
        dialog.setSize(420, 420);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        JLabel accLabel = new JLabel("Account No:");
        JTextField accField = new JTextField();
        JLabel nameLabel = new JLabel("Name:");
        JTextField nameField = new JTextField();
        JLabel genderLabel = new JLabel("Gender (M/F):");
        JTextField genderField = new JTextField();
        JLabel mobileLabel = new JLabel("Mobile:");
        JTextField mobileField = new JTextField();
        JLabel typeLabel = new JLabel("Account Type:");
        String[] types = {"Savings", "Current", "Fixed"};
        JComboBox<String> typeBox = new JComboBox<>(types);
        JLabel balLabel = new JLabel("Initial Balance:");
        JTextField balField = new JTextField();

        JButton ok = new JButton("Add");
        JButton cancel = new JButton("Cancel");

        int row = 0;
        g.gridx = 0; g.gridy = row; dialog.add(accLabel, g);
        g.gridx = 1; g.gridy = row++; dialog.add(accField, g);
        g.gridx = 0; g.gridy = row; dialog.add(nameLabel, g);
        g.gridx = 1; g.gridy = row++; dialog.add(nameField, g);
        g.gridx = 0; g.gridy = row; dialog.add(genderLabel, g);
        g.gridx = 1; g.gridy = row++; dialog.add(genderField, g);
        g.gridx = 0; g.gridy = row; dialog.add(mobileLabel, g);
        g.gridx = 1; g.gridy = row++; dialog.add(mobileField, g);
        g.gridx = 0; g.gridy = row; dialog.add(typeLabel, g);
        g.gridx = 1; g.gridy = row++; dialog.add(typeBox, g);
        g.gridx = 0; g.gridy = row; dialog.add(balLabel, g);
        g.gridx = 1; g.gridy = row++; dialog.add(balField, g);

        JPanel p = new JPanel();
        p.add(ok);
        p.add(cancel);
        g.gridx = 0; g.gridy = row; g.gridwidth = 2; dialog.add(p, g);

        ok.addActionListener(ev -> {
            String accNo = accField.getText().trim();
            String name = nameField.getText().trim();
            String gen = genderField.getText().trim();
            String mobile = mobileField.getText().trim();
            String type = (String) typeBox.getSelectedItem();
            String balStr = balField.getText().trim();

            if (accNo.isEmpty() || name.isEmpty() || balStr.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please fill Account No, Name and Balance.");
                return;
            }
            if (findAccount(accNo).isPresent()) {
                JOptionPane.showMessageDialog(dialog, "Account number already exists.");
                return;
            }
            double bal;
            try {
                bal = Double.parseDouble(balStr);
                if (bal < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid balance amount.");
                return;
            }

            Account a = new Account(accNo, name, gen, mobile, type, bal);
            accounts.add(a);
            saveAccounts();
            String entry;
            if ("EMPLOYEE".equals(creatorType)) {
                entry = String.format("EMP-ADD: Account %s (%s) created by %s with balance %.2f", accNo, name, EMP_ID, bal);
            } else {
                entry = String.format("ADD: Account %s (%s) created by ADMIN with balance %.2f", accNo, name, bal);
            }
            logChange(entry);
            refreshTable();
            JOptionPane.showMessageDialog(dialog, "Account added.");
            dialog.dispose();
        });

        cancel.addActionListener(ev -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void showDeleteDialog() {
        String accNo = JOptionPane.showInputDialog(frame, "Enter Account Number to delete:");
        if (accNo == null) return;
        Optional<Account> opt = findAccount(accNo.trim());
        if (opt.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Account not found.");
            return;
        }
        Account a = opt.get();
        int ok = JOptionPane.showConfirmDialog(frame,
                "Delete account " + a.accNo + " (" + a.name + ")?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            accounts.remove(a);
            saveAccounts();
            String entry = String.format("DELETE: Account %s (%s) removed by ADMIN", a.accNo, a.name);
            logChange(entry);
            refreshTable();
            JOptionPane.showMessageDialog(frame, "Account deleted.");
        }
    }

    // ---------------- Deposit / Withdraw ----------------
    private void showDepositDialog() {
        JPanel p = new JPanel(new GridLayout(2, 2, 6, 6));
        p.add(new JLabel("Account No:"));
        JTextField accField = new JTextField();
        p.add(accField);
        p.add(new JLabel("Amount to deposit:"));
        JTextField amtField = new JTextField();
        p.add(amtField);

        if (!isAdmin && !isEmployee) {
            // Pre-fill and lock account no field for user
            accField.setText(currentUserAccNo);
            accField.setEditable(false);
        }

        int res = JOptionPane.showConfirmDialog(frame, p, "Deposit", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;

        String accNo = accField.getText().trim();
        String amtStr = amtField.getText().trim();

        if (!isAdmin && !isEmployee && !accNo.equals(currentUserAccNo)) {
            JOptionPane.showMessageDialog(frame, "You may only deposit to your own account.");
            return;
        }

        Optional<Account> opt = findAccount(accNo);
        if (opt.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Account not found.");
            return;
        }
        double amt;
        try {
            amt = Double.parseDouble(amtStr);
            if (amt <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Invalid amount.");
            return;
        }
        Account a = opt.get();
        double old = a.balance;
        a.balance += amt;
        saveAccounts();
        String who = isEmployee ? ("EMP:" + EMP_ID) : (isAdmin ? "ADMIN" : "USER:" + currentUserAccNo);
        String entry = String.format("DEPOSIT by %s: %.2f to %s (%.2f -> %.2f)", who, amt, a.accNo, old, a.balance);
        logChange(entry);
        refreshTable();
        JOptionPane.showMessageDialog(frame, "Deposited " + amt + " to " + a.accNo + ". New balance: " + a.balance);
    }

    private void showWithdrawDialog() {
        JPanel p = new JPanel(new GridLayout(2, 2, 6, 6));
        p.add(new JLabel("Account No:"));
        JTextField accField = new JTextField();
        p.add(accField);
        p.add(new JLabel("Amount to withdraw:"));
        JTextField amtField = new JTextField();
        p.add(amtField);

        if (!isAdmin && !isEmployee) {
            accField.setText(currentUserAccNo);
            accField.setEditable(false);
        }

        int res = JOptionPane.showConfirmDialog(frame, p, "Withdraw", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;

        String accNo = accField.getText().trim();
        String amtStr = amtField.getText().trim();

        if (!isAdmin && !isEmployee && !accNo.equals(currentUserAccNo)) {
            JOptionPane.showMessageDialog(frame, "You may only withdraw from your own account.");
            return;
        }

        Optional<Account> opt = findAccount(accNo);
        if (opt.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Account not found.");
            return;
        }
        double amt;
        try {
            amt = Double.parseDouble(amtStr);
            if (amt <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Invalid amount.");
            return;
        }
        Account a = opt.get();
        if (a.balance < amt) {
            JOptionPane.showMessageDialog(frame, "Insufficient funds. Current balance: " + a.balance);
            return;
        }
        double oldBal = a.balance;
        a.balance -= amt;
        saveAccounts();
        String who = isEmployee ? ("EMP:" + EMP_ID) : (isAdmin ? "ADMIN" : "USER:" + currentUserAccNo);
        String entry = String.format("WITHDRAW by %s: %.2f from %s (%.2f -> %.2f)", who, amt, a.accNo, oldBal, a.balance);
        logChange(entry);
        refreshTable();
        JOptionPane.showMessageDialog(frame, "Withdrew " + amt + " from " + a.accNo + ". New balance: " + a.balance);
    }

    // ---------------- Find / View ----------------
    private void showFindDialog() {
        String accNo = JOptionPane.showInputDialog(frame, "Enter Account Number to find:");
        if (accNo == null) return;
        Optional<Account> opt = findAccount(accNo.trim());
        if (opt.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Account not found.");
            return;
        }
        Account a = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Account No: ").append(a.accNo).append("\n");
        sb.append("Name: ").append(a.name).append("\n");
        sb.append("Gender: ").append(a.gender).append("\n");
        sb.append("Mobile: ").append(a.mobile).append("\n");
        sb.append("Type: ").append(a.type).append("\n");
        sb.append("Balance: ").append(a.balance).append("\n");
        JOptionPane.showMessageDialog(frame, sb.toString(), "Account Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showFindDialogForUser() {
        if (currentUserAccNo == null) return;
        Optional<Account> opt = findAccount(currentUserAccNo);
        if (opt.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Your account was not found.");
            return;
        }
        Account a = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Account No: ").append(a.accNo).append("\n");
        sb.append("Name: ").append(a.name).append("\n");
        sb.append("Gender: ").append(a.gender).append("\n");
        sb.append("Mobile: ").append(a.mobile).append("\n");
        sb.append("Type: ").append(a.type).append("\n");
        sb.append("Balance: ").append(a.balance).append("\n");
        JOptionPane.showMessageDialog(frame, sb.toString(), "My Account Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showViewAllDialog() {
        JDialog d = new JDialog(frame, "All Accounts", true);
        d.setSize(900, 500);
        d.setLocationRelativeTo(frame);

        String[] cols = {"Account No", "Name", "Gender", "Mobile", "Type", "Balance"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(tm);
        for (Account a : accounts) {
            tm.addRow(new Object[]{a.accNo, a.name, a.gender, a.mobile, a.type, a.balance});
        }
        d.setLayout(new BorderLayout());
        d.add(new JScrollPane(t), BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(e -> d.dispose());
        JPanel p = new JPanel();
        p.add(close);
        d.add(p, BorderLayout.SOUTH);

        d.setVisible(true);
    }

    // ---------------- Loan Module ----------------
    private void showApplyLoanDialog() {
        JPanel panel = new JPanel(new GridLayout(5, 2, 6, 6));
        panel.add(new JLabel("Applicant Account No:"));
        JTextField accField = new JTextField();
        panel.add(accField);
        panel.add(new JLabel("Loan Amount:"));
        JTextField amtField = new JTextField();
        panel.add(amtField);
        panel.add(new JLabel("Term (months):"));
        JTextField termField = new JTextField();
        panel.add(termField);
        panel.add(new JLabel("Purpose / Notes:"));
        JTextField purposeField = new JTextField();
        panel.add(purposeField);

        if (!isAdmin && !isEmployee) {
            accField.setText(currentUserAccNo);
            accField.setEditable(false);
        }

        int res = JOptionPane.showConfirmDialog(frame, panel, "Apply for Loan", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;

        String accNo = accField.getText().trim();
        String amtStr = amtField.getText().trim();
        String termStr = termField.getText().trim();
        String purpose = purposeField.getText().trim();

        if (accNo.isEmpty() || amtStr.isEmpty() || termStr.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please provide account number, amount and term.");
            return;
        }

        Optional<Account> opt = findAccount(accNo);
        if (opt.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Applicant account not found.");
            return;
        }

        double amount;
        int term;
        try {
            amount = Double.parseDouble(amtStr);
            term = Integer.parseInt(termStr);
            if (amount <= 0 || term <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Invalid amount or term.");
            return;
        }

        // create loan
        String loanId = "LN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String appliedBy = isEmployee ? "EMPLOYEE" : "USER";
        String appliedById = isEmployee ? EMP_ID : currentUserAccNo;
        Loan loan = new Loan(loanId, accNo, amount, term, purpose, LoanStatus.PENDING, appliedBy, appliedById);
        loans.add(loan);
        saveLoans();
        String entry = String.format("LOAN_APPLY: %s applied by %s(%s) for %.2f term %d", loanId, appliedBy, appliedById, amount, term);
        logChange(entry);
        JOptionPane.showMessageDialog(frame, "Loan submitted (ID: " + loanId + "). Admin will review.");
    }

    private void showMyLoansDialog() {
        JDialog d = new JDialog(frame, isAdmin ? "All Loans (Admin)" : (isEmployee ? "Loans Submitted by Employee" : "My Loans"), true);
        d.setSize(900, 500);
        d.setLocationRelativeTo(frame);
        d.setLayout(new BorderLayout());

        String[] cols = {"Loan ID", "Acc No", "Amount", "Term", "Purpose", "Status", "Applied By", "Applied ID"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(tm);
        for (Loan loan : loans) {
            if (isAdmin ||
                    (isEmployee && loan.appliedBy.equals("EMPLOYEE") && loan.appliedById.equals(EMP_ID)) ||
                    (!isAdmin && !isEmployee && loan.applicantAccNo.equals(currentUserAccNo))) {
                tm.addRow(new Object[]{loan.loanId, loan.applicantAccNo, loan.amount, loan.termMonths, loan.purpose, loan.status, loan.appliedBy, loan.appliedById});
            }
        }
        d.add(new JScrollPane(t), BorderLayout.CENTER);

        JPanel p = new JPanel();
        JButton close = new JButton("Close");
        close.addActionListener(e -> d.dispose());
        p.add(close);
        d.add(p, BorderLayout.SOUTH);

        d.setVisible(true);
    }

    private void showLoanDashboard() {
        JDialog d = new JDialog(frame, "Loan Dashboard (Admin)", true);
        d.setSize(1000, 600);
        d.setLocationRelativeTo(frame);
        d.setLayout(new BorderLayout());

        String[] cols = {"Loan ID", "Acc No", "Amount", "Term", "Purpose", "Status", "Applied By", "Applied ID"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(tm);
        for (Loan loan : loans) {
            tm.addRow(new Object[]{loan.loanId, loan.applicantAccNo, loan.amount, loan.termMonths, loan.purpose, loan.status, loan.appliedBy, loan.appliedById});
        }
        d.add(new JScrollPane(t), BorderLayout.CENTER);

        JPanel p = new JPanel();
        JButton approve = new JButton("Approve");
        JButton reject = new JButton("Reject");
        JButton close = new JButton("Close");

        approve.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(d, "Select a loan row first.");
                return;
            }
            String loanId = (String) tm.getValueAt(row, 0);
            Optional<Loan> opt = loans.stream().filter(l -> l.loanId.equals(loanId)).findFirst();
            if (opt.isEmpty()) return;
            Loan ln = opt.get();
            if (ln.status != LoanStatus.PENDING) {
                JOptionPane.showMessageDialog(d, "Only pending loans can be approved.");
                return;
            }
            // check for outstanding approved loan for this account
            boolean hasApproved = loans.stream().anyMatch(l -> l.applicantAccNo.equals(ln.applicantAccNo) && l.status == LoanStatus.APPROVED);
            if (hasApproved) {
                int r2 = JOptionPane.showConfirmDialog(d, "Applicant already has an approved loan. Approve another?","Confirm", JOptionPane.YES_NO_OPTION);
                if (r2 != JOptionPane.YES_OPTION) return;
            }
            ln.status = LoanStatus.APPROVED;
            saveLoans();
            String entry = String.format("LOAN_APPROVE: %s approved by ADMIN", ln.loanId);
            logChange(entry);
            tm.setValueAt(ln.status, row, 5);
            JOptionPane.showMessageDialog(d, "Loan approved.");
        });

        reject.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(d, "Select a loan row first.");
                return;
            }
            String loanId = (String) tm.getValueAt(row, 0);
            Optional<Loan> opt = loans.stream().filter(l -> l.loanId.equals(loanId)).findFirst();
            if (opt.isEmpty()) return;
            Loan ln = opt.get();
            if (ln.status != LoanStatus.PENDING) {
                JOptionPane.showMessageDialog(d, "Only pending loans can be rejected.");
                return;
            }
            String reason = JOptionPane.showInputDialog(d, "Enter rejection note (optional):");
            ln.status = LoanStatus.REJECTED;
            if (reason != null && !reason.trim().isEmpty()) {
                ln.purpose += " | Rejection note: " + reason.trim();
            }
            saveLoans();
            String entry = String.format("LOAN_REJECT: %s rejected by ADMIN (%s)", ln.loanId, reason == null ? "" : reason);
            logChange(entry);
            tm.setValueAt(ln.status, row, 5);
            JOptionPane.showMessageDialog(d, "Loan rejected.");
        });

        close.addActionListener(e -> d.dispose());

        p.add(approve);
        p.add(reject);
        p.add(close);
        d.add(p, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    // ---------------- Change Log ----------------
    private void showChangeLogDialog() {
        JDialog d = new JDialog(frame, "Data Change Log (Admin)", true);
        d.setSize(800, 500);
        d.setLocationRelativeTo(frame);
        d.setLayout(new BorderLayout());

        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        StringBuilder sb = new StringBuilder();
        for (String s : changeLog) sb.append(s).append("\n");
        ta.setText(sb.toString());
        JScrollPane sp = new JScrollPane(ta);
        d.add(sp, BorderLayout.CENTER);

        JPanel p = new JPanel();
        JButton clear = new JButton("Clear Log");
        JButton close = new JButton("Close");
        clear.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(d, "Clear the change log file? This is permanent.", "Confirm", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                changeLog.clear();
                try { Files.deleteIfExists(new File(LOG_FILE).toPath()); } catch (Exception ex) {}
                ta.setText("");
            }
        });
        close.addActionListener(e -> d.dispose());
        p.add(clear);
        p.add(close);
        d.add(p, BorderLayout.SOUTH);

        d.setVisible(true);
    }

    private void showHelpDialog() {
        String help = "Banking System Help\n\n" +
                "Admin:\n" +
                " - Manage accounts, view all accounts, loan dashboard (approve/reject)\n" +
                "Employee:\n" +
                " - Create accounts on behalf of customers, submit loan applications, view submitted loans\n" +
                "User:\n" +
                " - Deposit/Withdraw, view own account, apply for loans, view own loans\n\n" +
                "Data files:\n" +
                " - Accounts: " + DATA_FILE + "\n" +
                " - Loans:    " + LOAN_FILE + "\n" +
                " - Change log: " + LOG_FILE + "\n\n" +
                "Employee credentials (persistent):\n" +
                " - ID: " + EMP_ID + "\n" +
                " - Password: " + EMP_PASSWORD + "\n";
        JTextArea ta = new JTextArea(help);
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(520, 320));
        JOptionPane.showMessageDialog(frame, sp, "Help", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------------- Persistence & Utilities ----------------
    @SuppressWarnings("unchecked")
    private ArrayList<Account> loadAccounts() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            Object obj = ois.readObject();
            if (obj instanceof ArrayList) {
                return (ArrayList<Account>) obj;
            } else {
                System.err.println("Data file format mismatch - starting with empty list.");
                return new ArrayList<>();
            }
        } catch (Exception ex) {
            System.err.println("Failed to load accounts: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveAccounts() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(accounts);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Failed to save data: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ArrayList<Loan> loadLoans() {
        File f = new File(LOAN_FILE);
        if (!f.exists()) return new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            Object obj = ois.readObject();
            if (obj instanceof ArrayList) {
                return (ArrayList<Loan>) obj;
            } else {
                System.err.println("Loan file format mismatch - starting with empty list.");
                return new ArrayList<>();
            }
        } catch (Exception ex) {
            System.err.println("Failed to load loans: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveLoans() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(LOAN_FILE))) {
            oos.writeObject(loans);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Failed to save loans: " + ex.getMessage());
        }
    }

    private Optional<Account> findAccount(String accNo) {
        return accounts.stream().filter(a -> a.accNo.equals(accNo)).findFirst();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Account a : accounts) {
            if (!isAdmin && !isEmployee && currentUserAccNo != null && !a.accNo.equals(currentUserAccNo)) continue;
            tableModel.addRow(new Object[]{a.accNo, a.name, a.gender, a.mobile, a.type, a.balance});
        }
    }

    private ArrayList<String> loadChangeLog() {
        ArrayList<String> list = new ArrayList<>();
        File f = new File(LOG_FILE);
        if (!f.exists()) return list;
        try {
            for (String line : Files.readAllLines(f.toPath())) list.add(line);
        } catch (Exception ex) { /* ignore */ }
        return list;
    }

    private void logChange(String entry) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = ts + " - " + entry;
        changeLog.add(line);
        try {
            Files.write(new File(LOG_FILE).toPath(), (line + System.lineSeparator()).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            System.err.println("Failed to write log: " + ex.getMessage());
        }
    }

    // ---------------- Data classes ----------------
    public static class Account implements Serializable {
        private static final long serialVersionUID = 1L;
        String accNo;
        String name;
        String gender;
        String mobile;
        String type;
        double balance;

        public Account(String accNo, String name, String gender, String mobile, String type, double balance) {
            this.accNo = accNo;
            this.name = name;
            this.gender = gender;
            this.mobile = mobile;
            this.type = type;
            this.balance = balance;
        }
    }

    public enum LoanStatus { PENDING, APPROVED, REJECTED }

    public static class Loan implements Serializable {
        private static final long serialVersionUID = 1L;
        String loanId;
        String applicantAccNo;
        double amount;
        int termMonths;
        String purpose;
        LoanStatus status;
        String appliedBy;   // "USER" or "EMPLOYEE"
        String appliedById; // accountNo for user or EMP_ID for employee

        public Loan(String loanId, String applicantAccNo, double amount, int termMonths, String purpose, LoanStatus status, String appliedBy, String appliedById) {
            this.loanId = loanId;
            this.applicantAccNo = applicantAccNo;
            this.amount = amount;
            this.termMonths = termMonths;
            this.purpose = purpose;
            this.status = status;
            this.appliedBy = appliedBy;
            this.appliedById = appliedById;
        }
    }

    // ---------------- Main ----------------
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(BankSystemSingle::new);
    }
}
