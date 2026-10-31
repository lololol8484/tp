# User Guide

## Introduction

FinNUS is a desktop finance tracker designed for tech-savvy university students managing a tight monthly budget. Whether you are an international student juggling foreign currency allowances or a local student managing daily campus expenses, FinNUS provides a streamlined Command Line Interface (CLI) to help you determine exactly how much is safe to spend each day.

## Quick Start

1. Ensure that JDK 25 is installed on your computer.
   - On macOS with SDKMAN, select the project version with `sdk use java 25.0.3.fx-zulu`.
2. Download the latest `finnus.jar` file from here.
3. Copy the file to the folder you want to use as the home folder for your FinNUS.
4. Open a terminal, `cd` to the folder containing the JAR file, and run the application using the following command:

   ```bash
   java -jar finnus.jar
   ```

5. The first time you run FinNUS, it asks for your opening balance, and optionally your monthly allowance. See [First-run setup](#first-run-setup-opening-balance).
6. Type a command in the CLI and press Enter to execute it. For example, type `help` and press Enter to display a table of command names, descriptions and syntax so you can look up how to use them.
7. Refer to the Features section below for details of each command.

## Saving and recovering your data

FinNUS automatically saves transactions, the opening balance, and exchange rates whenever they change. The main save
file is `finnus.txt`, with the previous saved state kept in `finnus.txt.bak`.

On a first run with no existing ledger or backup, FinNUS creates a pending setup file and asks for an opening balance.
The balance is saved only after you confirm it. If you exit or input ends before confirmation, the pending setup state
remains and FinNUS asks you to complete setup the next time you run it.

If a data row is corrupt, FinNUS skips it and reports a warning; the recovered ledger may be incomplete.
If the main file is unusable, FinNUS tries its backup. If neither can be recovered, it starts with an empty ledger in memory and leaves the original file untouched until data changes.

## Features 

<!-- @@author nabijonzzz -->
Every transaction records the day it happened, shown at the start, e.g. `[30 Oct 2026]`. The examples below assume
that today is 30 Oct 2026.
<!-- @@author -->

### Adding a deposit: `deposit`
Records money you receive, such as your monthly allowance, and adds it to your balance.

Format: `deposit AMOUNT [d/DESCRIPTION] [c/CURRENCY] [on/DATE]`

* `AMOUNT` is in SGD (unless `c/CURRENCY` is given) and must be a positive number with at most 2 decimal places, e.g. `500`, `12.5` or `4.50`.
* `AMOUNT` must not be more than `1000000`. Do not include `$` or commas.
* `DESCRIPTION` is optional. Everything after `d/` is taken as the description.
* `AMOUNT` and `d/DESCRIPTION` can be given in either order.
* `DATE` is optional and defaults to today. See [Recording the date of a transaction](#recording-the-date-of-a-transaction-on).
* To record an amount in another currency, add `c/CURRENCY`. See [Recording an amount in another currency](#recording-an-amount-in-another-currency-c).

Examples:

* `deposit 500 d/Monthly Allowance`
* `deposit d/Part-time job 120.50`
* `deposit 50`

Expected output for `deposit 500 d/Monthly Allowance`:
```
____________________________________________________________
Deposit added:
  [30 Oct 2026] [Deposit] +$500.00 (Monthly Allowance)
You now have 1 transaction(s).
____________________________________________________________
```

### Adding a withdrawal: `withdraw`
Records money you spend and removes it from your balance.

Format: `withdraw AMOUNT d/DESCRIPTION [c/CURRENCY] [t/CATEGORY] [on/DATE]`

* `AMOUNT` follows the same rules as for `deposit`.
* `DESCRIPTION` is required, so you always know what the money was spent on.
* `AMOUNT` and `d/DESCRIPTION` can be given in either order.
* `DATE` is optional and defaults to today. See [Recording the date of a transaction](#recording-the-date-of-a-transaction-on).
* `CATEGORY` is optional. If you leave it out, the spending is recorded under `Others`.
  See [Categorising your spending](#categorising-your-spending-t-and-category).

Examples:

* `withdraw 4.50 d/Lunch`
* `withdraw d/Chicken rice 4.50 t/food`

Expected output for `withdraw 4.50 d/Lunch`:
```
____________________________________________________________
Withdrawal added:
  [30 Oct 2026] [Withdrawal] -$4.50 (Lunch) #Others
You now have 2 transaction(s).
____________________________________________________________
```

Example of an invalid command, `withdraw 4.50` (no description):
```
____________________________________________________________
Error: A withdrawal needs a description so you know what you spent on.
Format: withdraw AMOUNT d/DESCRIPTION [c/CURRENCY] [t/CATEGORY] [on/DATE]
____________________________________________________________
```

### First-run setup: opening balance
The first time you run FinNUS, it asks how much money you currently have, so your balance starts from an accurate figure.

* Enter the amount in SGD. It follows the same rules as for `deposit`, except that `0` is also allowed.
* Answer `yes` (or `y`) to confirm, or `no` (or `n`) to enter a different amount.
* The opening balance is not a transaction, so it does not show up in `recent` and cannot be removed with `delete`.
* The ledger is not changed until you confirm. An invalid confirmation answer repeats the confirmation prompt.
* If you close the application or input ends before confirmation, setup is incomplete and will resume the next time you
  run FinNUS.
* After confirmation, FinNUS asks about your [allowance](#setting-your-monthly-allowance-allowance), which you can
  skip, and then normal commands begin. Afterwards, `opening` is not a command.

Example of first-run setup, entering `1200.50` and then `yes`:
```
____________________________________________________________
First-time setup
Enter your current opening balance in SGD.
This is your starting balance and will not be recorded as a transaction.
____________________________________________________________
Opening balance (SGD): 1200.50
Confirm opening balance $1200.50 SGD? [yes/no]: yes
```

<!-- @@author chancheese -->
Next, FinNUS asks when your monthly allowance arrives and how much it is. Both are optional:

* Enter the day of the month it arrives (1 to 31), then its amount in SGD.
* Press Enter at the day to skip the allowance, or at the amount to only count the days until it arrives.
* For an allowance in another currency, skip this and use [`allowance`](#setting-your-monthly-allowance-allowance)
  after setting an exchange rate.

Example, entering `5` and then `500` on 30 Oct 2026:
```
____________________________________________________________
Optional: when does your monthly allowance arrive?
FinNUS can count the days until it arrives and add it for you each month.
Press Enter to skip; you can set it later with: allowance 5 a/500
____________________________________________________________
Allowance day (1-31): 5
Allowance amount in SGD (Enter to only count days): 500
____________________________________________________________
Allowance set.
Allowance: $500.00 on day 5 (added automatically)
This cycle: 5 Oct 2026 - 4 Nov 2026
Next allowance in 6 days (5 Nov 2026).
Received this cycle: $0.00 in 0 deposit(s).
Spent this cycle: $0.00 in 0 withdrawal(s).
From 5 Nov 2026, FinNUS adds it as a deposit for you, so you do not need to record it.
____________________________________________________________
____________________________________________________________
Welcome to FinNUS! Type a command to get started.
____________________________________________________________
```
<!-- @@author -->

### Setting an exchange rate: `rate`
Sets how much of another currency 1 SGD buys, so FinNUS can convert amounts in that currency to SGD.
Rates are entered by you rather than fetched online, so FinNUS works without internet.

Format: `rate CURRENCY RATE` to set a rate, or `rate` to list all rates.

* `CURRENCY` is a 3-letter currency code such as `JPY`, `USD` or `MYR`, in upper or lower case. `SGD` cannot be given a rate, because all amounts are already in SGD.
* `RATE` is how many units of `CURRENCY` 1 SGD buys, e.g. `110` for 1 SGD = 110 JPY. It must be a positive number with at most 6 decimal places, and not more than `1000000`.
* Setting a rate for a currency that already has one replaces the old rate, and the old rate is shown.
* If no rates are set yet, `rate` shows `No exchange rates set yet.` with an example.

Examples:

* `rate JPY 110`
* `rate usd 0.74`
* `rate`

Expected output for `rate JPY 110`:
```
____________________________________________________________
Exchange rate set: 1 SGD = 110 JPY
____________________________________________________________
```

Expected output for `rate JPY 112` when JPY already has a rate:
```
____________________________________________________________
Exchange rate set: 1 SGD = 112 JPY (was 1 SGD = 110 JPY)
____________________________________________________________
```

Expected output for `rate` after setting rates for JPY and MYR:
```
____________________________________________________________
Exchange rates:
  1 SGD = 110 JPY
  1 SGD = 3.45 MYR
____________________________________________________________
```

<!-- @@author chancheese -->
### Recording an amount in another currency: `c/`
Add `c/CURRENCY` to a `deposit` or `withdraw` to record an amount in another currency, e.g. while on exchange.
FinNUS converts it to SGD using the rate you set with `rate`, and keeps the original amount so you can see it later.

Format: `deposit AMOUNT [d/DESCRIPTION] c/CURRENCY` or `withdraw AMOUNT d/DESCRIPTION c/CURRENCY`

* `CURRENCY` is a 3-letter currency code, in upper or lower case. You must set a rate for it first with `rate`.
* `AMOUNT` is in `CURRENCY` and must be a positive number with at most 2 decimal places. After converting, it is rounded to the nearest cent, and must be at least `$0.01` and at most `$1000000`.
* `c/CURRENCY` can be given anywhere after the command word, but only once. `c/SGD` is the same as leaving it out.
* The rate is saved with the transaction, so changing a rate later does not change transactions you have already recorded.
* Because `c/` marks the currency, a word starting with `c/` cannot be used inside a description.

Examples:

* `withdraw 1100 d/Ramen c/JPY`
* `deposit c/usd 50`

Expected output for `withdraw 1100 d/Ramen c/JPY` after `rate JPY 110`:
```
____________________________________________________________
Withdrawal added:
  [30 Oct 2026] [Withdrawal] -$10.00 (Ramen) [JPY 1100] #Others
Converted at 1 SGD = 110 JPY.
You now have 1 transaction(s).
____________________________________________________________
```

Example of an invalid command, `deposit 50 c/USD` when no USD rate is set:
```
____________________________________________________________
Error: No exchange rate set for USD. Set one first with: rate USD RATE
____________________________________________________________
```
<!-- @@author -->

<!-- @@author nabijonzzz -->
### Categorising your spending: `t/` and `category`
Add `t/CATEGORY` to a `withdraw` to record what kind of spending it was, so you can later see where your money went.
Every withdrawal has exactly one category. Deposits do not have categories.

Format: `withdraw AMOUNT d/DESCRIPTION t/CATEGORY`

* These categories are always available: `Food`, `Transport`, `Housing`, `Bills`, `Shopping`, `Entertainment`,
  `Education`, `Health` and `Others`.
* If you leave out `t/CATEGORY`, the withdrawal is recorded under `Others`.
* `CATEGORY` is not case-sensitive. It is shown in the spelling it was created with, so `t/FOOD` is shown as `#Food`.
* `t/CATEGORY` can be given anywhere after the command word, but only once. It also works together with `c/CURRENCY`.
* A category that does not exist is rejected, so a typo such as `t/fod` never creates a new category by accident.
* Because `t/` marks the category, a word starting with `t/` cannot be used inside a description.

Examples:

* `withdraw 4.50 d/Chicken rice t/food`
* `withdraw t/transport d/Bus 1.20`
* `withdraw 1100 c/JPY d/Ramen t/food`

Expected output for `withdraw 4.50 d/Chicken rice t/food`:
```
____________________________________________________________
Withdrawal added:
  [30 Oct 2026] [Withdrawal] -$4.50 (Chicken rice) #Food
You now have 1 transaction(s).
____________________________________________________________
```

### Managing categories: `category`
Use `category` to list all predefined and custom spending categories. Use `category NAME` to add a custom category that
you can then use with `t/`.

Formats: `category` or `category NAME`

* `NAME` is one word made of letters, digits and `-`, at most 20 characters, e.g. `Gym` or `bubble-tea`.
* `NAME` must not already exist, ignoring case. For example, `category FOOD` is rejected because `Food` exists.
* `add` and `list` are valid category names when entered alone: `category add` creates `add`, and `category list` creates `list`.

Example: `category Gym`, then `withdraw 30 d/Membership t/gym`
```
____________________________________________________________
Category added: #Gym
Use it with, e.g.: withdraw 4.50 d/Lunch t/Gym
____________________________________________________________
```

Example output for `category`:
```
____________________________________________________________
Categories:
  Predefined: Food, Transport, Housing, Bills, Shopping, Entertainment, Education, Health, Others
  Custom: Gym
____________________________________________________________
```
<!-- @@author -->

<!-- @@author nabijonzzz -->
### Recording the date of a transaction: `on/`
Every deposit and withdrawal records the day it happened. It is today by default, but you can add `on/DATE` to
record a transaction you forgot to enter earlier.

Format: `deposit AMOUNT [d/DESCRIPTION] on/DATE` or `withdraw AMOUNT d/DESCRIPTION on/DATE`

* `DATE` must be in `YYYY-MM-DD` format, e.g. `2026-10-20`, and must be a real date (`2026-02-30` is rejected).
* `DATE` must be on or after `2000-01-01`.
* `DATE` cannot be in the future, since you can only record money that has already moved.
* `on/DATE` can be given anywhere after the command word, but only once. It works together with `c/` and `t/`.
<!-- @@author lololol8484 -->
* Transactions are stored in calendar-date order; FinNUS does not record the time of day. `recent` and `find` show
  the newest dates first. For transactions on the same date, the most recently entered appears first. Undated
  transactions from older save files appear after dated transactions, with the most recently entered undated
  transaction first.
* Adding a backdated transaction can change the numbers shown by `recent` and `find`, so check the current list before
  using an index with `delete`.
* If transaction rows in `finnus.txt` are out of date order, FinNUS sorts them in memory when it starts. The file is
  rewritten in that order the next time FinNUS saves a change; a read-only run leaves the file as it was.
<!-- @@author nabijonzzz -->
* Transactions saved by older versions of FinNUS have no date, and are shown without one.
* Because `on/` marks the date, a word starting with `on/` cannot be used inside a description.

Examples:

* `withdraw 4.50 d/Lunch on/2026-10-20`
* `deposit 500 d/Allowance on/2026-10-01`
* `withdraw 1100 c/JPY d/Ramen t/food on/2026-10-25`

Expected output for `withdraw 4.50 d/Lunch on/2026-10-20`:
```
____________________________________________________________
Withdrawal added:
  [20 Oct 2026] [Withdrawal] -$4.50 (Lunch) #Others
You now have 1 transaction(s).
____________________________________________________________
```

Example of an invalid command, `withdraw 4.50 d/Lunch on/2026-11-01` (a future date):
```
____________________________________________________________
Error: The date 2026-11-01 is in the future. Transactions can only be recorded for today or earlier.
____________________________________________________________
```
<!-- @@author -->

<!-- @@author chancheese -->
### Setting your monthly allowance: `allowance`
Tells FinNUS when your allowance arrives and how much it is. FinNUS then adds it as a deposit each month, and shows
how many days your money has to last.

Format: `allowance DAY [a/AMOUNT] [c/CURRENCY]`, `allowance` or `allowance off`

* `DAY` is the day of the month the allowance arrives, from 1 to 31. In shorter months, the last day of the month is
  used, e.g. `31` becomes 28 Feb.
* `AMOUNT` follows the same rules as for `deposit`. Without `a/AMOUNT`, FinNUS only counts the days.
* `c/CURRENCY` gives the currency of the amount, as for [`deposit`](#recording-an-amount-in-another-currency-c). It
  needs `a/AMOUNT`.
* Your budget cycle runs from one allowance day to the day before the next, e.g. 5 Oct to 4 Nov.
* `allowance` on its own shows a summary of the current cycle. Running `allowance DAY ...` again replaces the old
  setting.
* `allowance off` stops it. Deposits that were already added are kept.

How the allowance is added:

* When FinNUS starts, it adds a deposit described as `Allowance` for each allowance day that has passed, dated on that
  day. If FinNUS was closed for two months, both months are added.
* It starts from the **next** allowance day, never an earlier one, so it will not double an allowance you already
  recorded yourself.
* An amount in another currency is converted at the exchange rate set at the time it is added. If no rate is set,
  FinNUS waits and adds it once you set one with `rate`.
* If you delete an allowance deposit, it is not added again. Changing the amount only affects later deposits.

The summary counts deposits and withdrawals dated in the current cycle, in SGD. Transactions saved by older versions of
FinNUS have no date, so they are not counted.

Examples:

* `allowance 5 a/500`
* `allowance 25 a/60000 c/JPY`
* `allowance 31`
* `allowance`
* `allowance off`

Expected output for `allowance 5 a/500`:
```
____________________________________________________________
Allowance set.
Allowance: $500.00 on day 5 (added automatically)
This cycle: 5 Oct 2026 - 4 Nov 2026
Next allowance in 6 days (5 Nov 2026).
Received this cycle: $0.00 in 0 deposit(s).
Spent this cycle: $0.00 in 0 withdrawal(s).
From 5 Nov 2026, FinNUS adds it as a deposit for you, so you do not need to record it.
____________________________________________________________
```

Expected output for `allowance`, after recording an allowance and three withdrawals this cycle:
```
____________________________________________________________
Allowance: $500.00 on day 5 (added automatically)
This cycle: 5 Oct 2026 - 4 Nov 2026
Next allowance in 6 days (5 Nov 2026).
Received this cycle: $500.00 in 1 deposit(s).
Spent this cycle: $18.30 in 3 withdrawal(s).
____________________________________________________________
```

Expected output when FinNUS starts on or after 5 Nov 2026:
```
____________________________________________________________
Welcome to FinNUS! Type a command to get started.
____________________________________________________________
____________________________________________________________
Allowance added: [5 Nov 2026] [Deposit] +$500.00 (Allowance)
____________________________________________________________
```

Example of an allowance that cannot be added yet, `allowance 5 a/60000 c/JPY` with no rate set for JPY:
```
____________________________________________________________
Your allowance for 5 Nov 2026 was not added yet. No exchange rate set for JPY. Set one first with: rate JPY RATE
____________________________________________________________
```
<!-- @@author -->

### Viewing balance: `balance`
Shows the remaining balance of the user's account. 

Format: `balance`

* No parameters required. Additional text after `balance` is ignored.
* Does not modify existing transactions

Example: `balance` or `balance 123`

Expected output:
```text
____________________________________________________________
Your current balance is: $100.00
____________________________________________________________
```

<!-- @@author chancheese -->
If you have [set your allowance](#setting-your-monthly-allowance-allowance), `balance` also shows how many days are
left until it arrives:
```text
____________________________________________________________
Your current balance is: $581.70
Next allowance in 6 days (5 Nov 2026).
____________________________________________________________
```
<!-- @@author -->

### Viewing recent transactions: `recent`
Shows the user's most recent transactions, ordered by recency with the newest transaction first.

Format: `recent [NUMBER]`

* `[NUMBER]` is optional. It is set to 5 by default.
* If `[NUMBER]` is more than the total number of transactions, the program outputs the entire list of transactions.
* `[NUMBER]` must be positive 

Examples:

* `recent`
* `recent 10`

Example output for `recent 2`:
```text
____________________________________________________________
Here is a list of the recent 2 transaction(s):
1. [30 Oct 2026] [Deposit] +$30.50
2. [29 Oct 2026] [Withdrawal] -$30.00 (Lunch) #Food

```

<!-- @@author nabijonzzz -->
### Finding transactions: `find`
Lists the transactions whose description contains a keyword, so you can find a past transaction without scrolling
through all of them.

Format: `find KEYWORD`

* The search is not case-sensitive, and matches part of a word, e.g. `lun` matches `Lunch`.
* `KEYWORD` can be more than one word. The whole phrase is searched, e.g. `find chicken rice`.
* Only descriptions are searched, not amounts or categories.
* Results are shown from the most recent. Each one has the same number as in `recent`, so you can remove it straight
  away with `delete`.

Examples:

* `find lunch`
* `find chicken rice`

Expected output for `find lunch`:
```
____________________________________________________________
Found 2 transaction(s) matching "lunch":
1. [30 Oct 2026] [Withdrawal] -$12.00 (Lunch with friends) #Food
3. [28 Oct 2026] [Withdrawal] -$4.50 (Lunch) #Food
Use the number with delete to remove a transaction.
____________________________________________________________
```
<!-- @@author -->

### Deleting a transaction: `delete`
Deletes a transaction permanently.

Format: `delete INDEX`

* `INDEX` uses the list of recent transactions as reference. Use `recent` to get the necessary index of the transaction to delete.
* `INDEX` must be within the range of the transaction list's size.

Example:

* `delete 1` permanently deletes the most recent transaction.

Example output for `delete 3`:
```text
Transaction deleted: [27 Oct 2026] [Withdrawal] -$30.00 (BBQ) #Food

```

### Viewing command usage: `help`
Displays a table of command names, descriptions and syntax so you can look up how to use them.

Format: `help`

* No parameters are required. Extra text after `help` is ignored, so `help 123` displays the same table as `help`.
* Help does not change your transactions or exit the application.
* In the displayed syntax, `[]` means optional.

Example: `help` or `help 123`

Expected output:
```text
____________________________________________________________
Available commands:
+--------------+----------------------+----------------------------------------+
| Command      | Description          | Syntax                                 |
+--------------+----------------------+----------------------------------------+
| deposit      | Record income        | deposit AMOUNT [d/DESCRIPTION]         |
|              |                      | [c/CURRENCY] [on/DATE]                 |
| withdraw     | Record spending      | withdraw AMOUNT d/DESCRIPTION          |
|              |                      | [c/CURRENCY] [t/CATEGORY] [on/DATE]    |
| recent       | Recent activity      | recent [NUMBER]                        |
| find         | Search descriptions  | find KEYWORD                           |
| delete       | Delete a record      | delete INDEX                           |
| balance      | Current balance      | balance                                |
| allowance    | Monthly allowance    | allowance [DAY [a/AMOUNT]              |
|              |                      | [c/CURRENCY]] or off                   |
| rate         | Manage FX rates      | rate [CURRENCY RATE]                   |
| category     | Manage categories    | category [NAME]                        |
| help         | List commands        | help                                   |
| exit         | Close FinNUS         | exit                                   |
+--------------+----------------------+----------------------------------------+

[] means optional.
____________________________________________________________
```

## FAQ

**Q**: How do I transfer my data to another computer?

**A**: Copy `finnus.txt` (and `finnus.txt.bak`, if it exists) from the folder containing `finnus.jar` into the folder
where you put `finnus.jar` on the other computer. FinNUS loads it automatically the next time it starts.

**Q**: What is the `finnus.log` file that appears next to `finnus.jar`?

**A**: It is a log file that records what FinNUS did, to help developers investigate problems. It is not needed to use
FinNUS, and it is safe to delete. Your financial data is stored in `finnus.txt`, not in the log.

**Q**: Why does `opening` say it is not a command?

**A**: The opening balance can only be set during the first-run setup. After that, record changes to your money with
`deposit` and `withdraw` instead.

<!-- @@author chancheese -->
**Q**: My allowance was counted twice. What happened?

**A**: You probably recorded it with `deposit` as well as setting an amount with `allowance`. Remove one of them with
`delete`. FinNUS does not add a deleted allowance again.
<!-- @@author -->

## Command Summary

* Add a deposit: `deposit AMOUNT [d/DESCRIPTION] [c/CURRENCY] [on/DATE]`
* Add a withdrawal: `withdraw AMOUNT d/DESCRIPTION [c/CURRENCY] [t/CATEGORY] [on/DATE]`
* View balance: `balance`
* Set your monthly allowance: `allowance DAY [a/AMOUNT] [c/CURRENCY]`
* View or stop your allowance: `allowance` or `allowance off`
* View recent transactions: `recent [NUMBER]`
* Find transactions by description: `find KEYWORD`
* Delete a transaction: `delete INDEX`
* Set or list exchange rates: `rate [CURRENCY RATE]`
* Add a custom category: `category NAME`
* List categories: `category`
* View command usage: `help`
* Exit the application: `exit`
