```markdown
# tech Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill provides guidance on contributing to the `tech` JavaScript codebase, which is organized without a formal framework. It covers file and code conventions, typical workflows (such as extending features across multiple UI views), and testing patterns. The repository emphasizes modular, maintainable code with consistent naming and structure.

## Coding Conventions

- **File Naming:**  
  Use kebab-case for all file names.  
  _Example:_  
  ```
  boutique/data.js
  boutique/panier.js
  boutique/style.css
  ```

- **Import Style:**  
  Use relative imports for modules.  
  _Example:_  
  ```js
  import { getProduct } from './data.js';
  ```

- **Export Style:**  
  Use named exports.  
  _Example:_  
  ```js
  // In data.js
  export const products = [...];
  export function getProduct(id) { ... }
  ```

- **Commit Messages:**  
  Freeform, typically concise (average 56 characters), with or without prefixes.

## Workflows

### Feature Extension Across Multiple Views
**Trigger:** When you need to add or update a cross-cutting feature (e.g., product images) that affects product display, cart, and order summary.  
**Command:** `/extend-feature-across-views`

1. **Add or update data/assets**  
   Place new SVG images or other assets in `boutique/images/`.
   ```
   boutique/images/new-product.svg
   ```
2. **Update data source**  
   Reference new assets or fields in `boutique/data.js`.
   ```js
   export const products = [
     { id: 1, name: 'T-shirt', image: './images/tshirt.svg' },
     // ...
   ];
   ```
3. **Update all relevant UI scripts**  
   Modify scripts such as `boutique.js`, `panier.js`, `produit.js`, and `contact.js` to use the new feature.
   ```js
   // In boutique.js
   import { products } from './data.js';
   // Render product images where appropriate
   ```
4. **Update HTML templates**  
   If necessary, adjust HTML to accommodate new UI elements.
   ```html
   <img src="./images/tshirt.svg" alt="T-shirt" />
   ```
5. **Update styles**  
   Modify `boutique/style.css` to style the new elements.
   ```css
   .product-image {
     width: 80px;
     height: auto;
   }
   ```
6. **Update documentation**  
   Reflect the new feature in `boutique/README.md`.
7. **Add or update build scripts**  
   If asset generation is needed, update or create scripts like `build-images.js`.

## Testing Patterns

- **Test File Naming:**  
  Test files follow the `*.test.*` pattern.  
  _Example:_  
  ```
  boutique/data.test.js
  ```

- **Testing Framework:**  
  The specific framework is not detected.  
  _Tip:_ Use descriptive test names and keep tests close to the code they cover.

## Commands

| Command                        | Purpose                                                      |
|--------------------------------|--------------------------------------------------------------|
| /extend-feature-across-views   | Extend a feature (e.g., product images) across all UI views  |
```
