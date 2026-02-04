# Custom UI apps

In this folder, you can add custom (web) applications that will be shipped along with OpenRemote.  
For example, special mobile apps for end users, or apps for less-technical consumers are widespread.  

Developing these custom apps is pretty straightforward, thanks to the built-in packages we provide.  
These make communicating with OpenRemote easier, and allows developers to quickly set up a user interface.

## Example apps

We provided several example apps to get familiar with the architecture.  
All apps can be run using `npm run serve`, and visited at `http://localhost:9000/<your folder name>/`.  
Here's a list of the apps, and what they do;

### /custom
This is an example web application built with [Lit Web Components](https://lit.dev) and [Webpack](https://webpack.js.org).  
Apps in our main OpenRemote [repository](https://github.com/openremote/openremote) are built with these technologies as well.  
It can be used as a template to add your own pages on top of it.

### /custom-react
This is an example web application built with [React 19](https://react.dev) and [RSPack](https://rspack.rs).  
*(more information soon)*
