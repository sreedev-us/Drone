import matplotlib.pyplot as plt
import numpy as np
import math
import os

# Create docs directory for outputs if it doesn't exist
os.makedirs('docs', exist_ok=True)

# Data generation
n_values = np.arange(1, 13) # n from 1 to 12
n_values_dense = np.linspace(1, 12, 100)

# Brute Force: O(n!)
brute_force = [math.factorial(n) for n in n_values]

# Greedy: O(n^2)
greedy = n_values_dense ** 2

# Genetic Algorithm: O(G * P * n) where G=600, P=200 -> simplifies to O(n) scalar
# Scale it down for visualization purposes alongside others
genetic = 50 * n_values_dense 

# Plot 1: Standard Scale (shows how n! explodes)
plt.figure(figsize=(10, 6))
plt.plot(n_values, brute_force, label='Brute Force: O(n!)', color='red', marker='o')
plt.plot(n_values_dense, greedy, label='Greedy: C * O(n^2)', color='orange')
plt.plot(n_values_dense, genetic, label='Genetic: C * O(n)', color='green')

plt.ylim(0, 10000) # clip y-axis to see the polynomial and linear before factorial takes over entirely
plt.title('Algorithm Time Complexity (Standard Limit)')
plt.xlabel('Number of Deliveries (n)')
plt.ylabel('Operations')
plt.legend()
plt.grid(True, linestyle='--', alpha=0.7)
plt.tight_layout()
plt.savefig('docs/complexity_standard.png', dpi=300)
plt.close()

# Plot 2: Log Scale
plt.figure(figsize=(10, 6))
plt.plot(n_values, brute_force, label='Brute Force: O(n!)', color='red', marker='o')
plt.plot(n_values_dense, greedy, label='Greedy: C * O(n^2)', color='orange')
plt.plot(n_values_dense, genetic, label='Genetic: C * O(n)', color='green')

plt.yscale('log')
plt.title('Algorithm Time Complexity (Logarithmic Scale)')
plt.xlabel('Number of Deliveries (n)')
plt.ylabel('Operations (Log Scale)')
plt.legend()
plt.grid(True, linestyle='--', alpha=0.7)
plt.tight_layout()
plt.savefig('docs/complexity_log.png', dpi=300)
plt.close()

print("Graphs generated successfully in docs/ directory.")
