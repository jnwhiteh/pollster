$(function() {
  var baseUrl = "http://localhost:8080";

  var updateServerTable = function(entries) {
    var tbody = $("tbody#server-status");

    var contents = "";
    for (var i = 0; i < entries.length; i++) {
      contents = contents + "<tr>" +
        "<td>" + entries[i].name + "</td>" +
        "<td>" + entries[i].url + "</td>" +
        "<td>" + entries[i].status + "</td>" +
        "<td>" + entries[i].lastCheck + "</td>" +
        "<td>" + '<button type="button" data-service-id="' + entries[i].id + '" class="btn btn-info delete-entry">Delete</span></button>' + "</td>" +
        "</tr>\n";
    }

    tbody.html();
    tbody.html(contents);

    // bind to all delete buttons and make the delete API call
    $("button.delete-entry").click(function() {
      console.log("Got click for ", this);
      var $button = $(this);
      var serviceId = $button.data("service-id");
      var request = {
        "id": serviceId
      };

      $.ajax({
        url: baseUrl + "/service/" + serviceId,
        type: "DELETE",
        success: function(data) {
          updateServerList();
        }
      });
    });

  }

  var updateServerList = function() {
    var serverList = $.getJSON(baseUrl + "/service", function(data) {
      console.log(data);
      updateServerTable(data.services);
    });

  };

  // bind to the form submit button to handle adding a new entry
  $("button#add-service-submit").click(function() {
    var request = {
      "name": $("input#add-service-form-name").val(),
      "url": $("input#add-service-form-url").val()
    };

    $.ajax({
      url: baseUrl + "/service",
      type: "POST",
      data: JSON.stringify(request),
      contentType: "application/json; charset=utf-8",
      success: function(data) {
        console.log("Got response: ", data);
        $("#addServiceModal").modal("toggle");
        $("form#add-service-form").trigger("reset");
        updateServerList();
      }
    });
  });

  updateServerList();
});
