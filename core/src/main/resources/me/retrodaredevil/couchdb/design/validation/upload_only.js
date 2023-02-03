function(newDoc, oldDoc, userCtx, secObj) {
    if (!oldDoc) { // If this is a new document, then let it be uploaded
        return;
    }

    secObj.admins = secObj.admins || {};
    secObj.admins.names = secObj.admins.names || [];
    secObj.admins.roles = secObj.admins.roles || [];

    var isAdmin = false;
    if(userCtx.roles.indexOf('_admin') !== -1) {
        isAdmin = true;
    }
    if(secObj.admins.names.indexOf(userCtx.name) !== -1) {
        isAdmin = true;
    }
    for(var i = 0; i < userCtx.roles.length; i++) {
        if(secObj.admins.roles.indexOf(userCtx.roles[i]) !== -1) {
            isAdmin = true;
        }
    }

    if(!isAdmin) {
        throw {'unauthorized':'You are not authorized to change this document!'};
    }
}
